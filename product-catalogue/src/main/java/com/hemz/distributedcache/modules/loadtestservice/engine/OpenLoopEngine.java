package com.hemz.distributedcache.modules.loadtestservice.engine;

import org.HdrHistogram.Histogram;
import org.HdrHistogram.Recorder;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

/**
 * An OPEN-LOOP load generator: requests are sent on a fixed schedule,
 * whether or not earlier requests have finished.
 *
 * Latency is measured from each request's INTENDED send time, not from when it
 * actually left. If the server stalls, requests that should have gone out during
 * the stall are still charged the full wait. This is what avoids "coordinated
 * omission", the bug that makes closed-loop tools (like wrk) report latencies
 * far better than users really see.
 *
 * Each request runs on its own virtual thread, so a slow server never delays the
 * schedule. A semaphore caps how many are on the wire at once ("connections");
 * time spent waiting for a free slot counts toward latency, as it should.
 */
public final class OpenLoopEngine {

    static final long MAX_TRACKABLE_MICROS = TimeUnit.SECONDS.toMicros(60);
    static final int SIGNIFICANT_DIGITS = 3;
    /** The dispatcher sleeps at most this long between checks (sub-millisecond schedule resolution). */
    static final long TICK_NANOS = 250_000L;
    /** Time allowed to open a connection to the target. */
    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    /** How long to wait for the sampler thread to stop at the end of a step. */
    static final long SAMPLER_STOP_WAIT_SECONDS = 2;
    /** Extra time, on top of the request timeout, that queued requests get to finish after the window. */
    static final long DRAIN_MARGIN_MS = 2_000;

    /** Runs one step and returns its result. Blocks until the step (and its drain) is over. */
    public StepOutcome run(StepSpec spec, LoadListener listener, BooleanSupplier stopRequested) throws InterruptedException {
        return new StepRun(spec, listener, stopRequested).execute();
    }

    private static final class StepRun {

        private final StepSpec spec;
        private final LoadListener listener;
        private final BooleanSupplier stopRequested;
        private final String urlPrefix;
        private final HttpClient client;
        private final Semaphore slots;

        private final Recorder warmupRecorder = new Recorder(MAX_TRACKABLE_MICROS, SIGNIFICANT_DIGITS);
        private final Recorder measureRecorder = new Recorder(MAX_TRACKABLE_MICROS, SIGNIFICANT_DIGITS);
        private final Histogram steady = new Histogram(MAX_TRACKABLE_MICROS, SIGNIFICANT_DIGITS);

        private final AtomicLong intervalSucceeded = new AtomicLong();
        private final AtomicLong intervalErrors = new AtomicLong();
        private final AtomicLong inFlight = new AtomicLong();
        private final LongAdder succeededInWindow = new LongAdder();
        private final LongAdder totalCompleted = new LongAdder();
        private final LongAdder timeouts = new LongAdder();
        private final LongAdder connectErrors = new LongAdder();
        private final LongAdder non2xx = new LongAdder();
        private final LongAdder otherErrors = new LongAdder();

        private volatile long stepStart;
        private volatile long measureStart;
        private volatile long measureEnd;
        private long lastSampleNanos;

        StepRun(StepSpec spec, LoadListener listener, BooleanSupplier stopRequested) {
            this.spec = spec;
            this.listener = listener;
            this.stopRequested = stopRequested;
            this.urlPrefix = spec.targetBaseUrl() + "/api/products/";
            this.slots = new Semaphore(spec.connections());
            this.client = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .connectTimeout(CONNECT_TIMEOUT)
                    .build();
        }

        StepOutcome execute() throws InterruptedException {
            KeyGenerator keys = new KeyGenerator(spec.distribution(), spec.maxId(), spec.zipfExponent(), spec.seed());
            ScheduledExecutorService sampler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "loadtest-sampler");
                t.setDaemon(true);
                return t;
            });
            ExecutorService requests = Executors.newVirtualThreadPerTaskExecutor();

            stepStart = System.nanoTime();
            measureStart = stepStart + TimeUnit.SECONDS.toNanos(spec.warmupSeconds());
            measureEnd = measureStart + TimeUnit.SECONDS.toNanos(spec.durationSeconds());
            lastSampleNanos = stepStart;
            sampler.scheduleAtFixedRate(this::sample, 1, 1, TimeUnit.SECONDS);

            boolean stopped = false;
            long stoppedAt = 0;
            double nanosPerRequest = 1_000_000_000.0 / spec.rate();
            try {
                long i = 0;
                while (true) {
                    if (stopRequested.getAsBoolean()) {
                        stopped = true;
                        stoppedAt = System.nanoTime();
                        break;
                    }
                    long intended = stepStart + (long) (i * nanosPerRequest);
                    if (intended >= measureEnd) {
                        break;
                    }
                    long now = System.nanoTime();
                    if (intended <= now) {
                        long key = keys.next();
                        requests.execute(() -> send(intended, key));
                        i++;
                    } else {
                        LockSupport.parkNanos(Math.min(intended - now, TICK_NANOS));
                    }
                }
            } finally {
                if (stopped) {
                    requests.shutdownNow();
                } else {
                    drain(requests);
                }
                sampler.shutdownNow();
                sampler.awaitTermination(SAMPLER_STOP_WAIT_SECONDS, TimeUnit.SECONDS);
                sample();                       // flush the last partial second
                client.shutdownNow();
            }

            long windowEnd = stopped ? Math.min(stoppedAt, measureEnd) : measureEnd;
            double measuredSeconds = Math.max(0, windowEnd - measureStart) / 1e9;
            double achieved = measuredSeconds > 0 ? succeededInWindow.sum() / measuredSeconds : 0;
            LatencySummary latency;
            synchronized (this) {
                latency = LatencySummary.fromMicros(steady);
            }
            return new StepOutcome(
                    totalCompleted.sum(),
                    LatencySummary.round(achieved),
                    LatencySummary.round(measuredSeconds),
                    latency,
                    new ErrorCounts(timeouts.sum(), connectErrors.sum(), non2xx.sum(), otherErrors.sum()),
                    stopped);
        }

        /**
         * What counts, and why:
         *  - Throughput counts only 2xx responses. Errors are not "work delivered";
         *    counting them would make a dead server look healthy.
         *  - Warm-up requests are left out of the step's totals (completed, errors, throughput).
         *    They still show in the per-second samples.
         *  - Latency is recorded for every HTTP response (2xx or not) and for timeouts,
         *    because the user really waited that long. Connection and I/O errors are
         *    NOT recorded: they fail in microseconds and would drag latency down.
         */
        private void send(long intendedNanos, long key) {
            boolean measure = intendedNanos >= measureStart;
            inFlight.incrementAndGet();
            boolean gotResponse = false;
            boolean success = false;
            boolean timedOut = false;
            boolean interrupted = false;
            try {
                slots.acquire();
                try {
                    HttpRequest request = HttpRequest.newBuilder(URI.create(urlPrefix + key))
                            .timeout(spec.requestTimeout())
                            .GET()
                            .build();
                    HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
                    gotResponse = true;
                    int status = response.statusCode();
                    success = status >= 200 && status < 300;
                    if (!success) {
                        error(measure, non2xx);
                    }
                } finally {
                    slots.release();
                }
            } catch (HttpConnectTimeoutException | ConnectException e) {
                error(measure, connectErrors);
            } catch (HttpTimeoutException e) {
                timedOut = true;
                error(measure, timeouts);
            } catch (InterruptedException e) {
                interrupted = true;             // the step was stopped; this request never really finished
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                error(measure, otherErrors);
            } finally {
                long end = System.nanoTime();
                inFlight.decrementAndGet();
                if (!interrupted) {
                    if (measure) {
                        totalCompleted.increment();
                    }
                    if (gotResponse || timedOut) {
                        long micros = Math.min(MAX_TRACKABLE_MICROS, Math.max(1, (end - intendedNanos) / 1_000));
                        (measure ? measureRecorder : warmupRecorder).recordValue(micros);
                    }
                    if (success) {
                        intervalSucceeded.incrementAndGet();
                        if (measure && end <= measureEnd) {
                            succeededInWindow.increment();
                        }
                    }
                }
            }
        }

        private void error(boolean measure, LongAdder kind) {
            intervalErrors.incrementAndGet();
            if (measure) {
                kind.increment();
            }
        }

        private void drain(ExecutorService requests) throws InterruptedException {
            requests.shutdown();
            long waitMillis = spec.requestTimeout().toMillis() + DRAIN_MARGIN_MS;
            if (!requests.awaitTermination(waitMillis, TimeUnit.MILLISECONDS)) {
                listener.onLog("WARN", inFlight.get() + " requests still in flight after " + waitMillis + " ms; abandoning them");
                requests.shutdownNow();
            }
        }

        private synchronized void sample() {
            long now = System.nanoTime();
            Histogram warm = warmupRecorder.getIntervalHistogram();
            Histogram meas = measureRecorder.getIntervalHistogram();
            steady.add(meas);

            Histogram combined = new Histogram(MAX_TRACKABLE_MICROS, SIGNIFICANT_DIGITS);
            combined.add(warm);
            combined.add(meas);

            long succeeded = intervalSucceeded.getAndSet(0);
            long errors = intervalErrors.getAndSet(0);
            double seconds = Math.max(0.001, (now - lastSampleNanos) / 1e9);
            // A sample covers the interval since the previous one. Label it by that interval's
            // midpoint, not its end, or the last warm-up second would be counted as measurement.
            long mid = lastSampleNanos + (now - lastSampleNanos) / 2;
            lastSampleNanos = now;
            boolean any = combined.getTotalCount() > 0;

            listener.onInterval(new IntervalStats(
                    mid < measureStart ? "warmup" : mid <= measureEnd ? "measure" : "drain",
                    LatencySummary.round((now - stepStart) / 1e9),
                    errors,
                    LatencySummary.round(succeeded / seconds),
                    any ? LatencySummary.ms(combined, 50) : null,
                    any ? LatencySummary.ms(combined, 99) : null));
        }
    }
}
