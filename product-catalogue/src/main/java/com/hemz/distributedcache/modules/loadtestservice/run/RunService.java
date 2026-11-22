package com.hemz.distributedcache.modules.loadtestservice.run;

import com.hemz.distributedcache.modules.loadtestservice.config.LoadTestProperties;
import com.hemz.distributedcache.modules.loadtestservice.engine.IntervalStats;
import com.hemz.distributedcache.modules.loadtestservice.engine.OpenLoopEngine;
import com.hemz.distributedcache.modules.loadtestservice.engine.LoadListener;
import com.hemz.distributedcache.modules.loadtestservice.engine.StepOutcome;
import com.hemz.distributedcache.modules.loadtestservice.engine.StepSpec;
import com.hemz.distributedcache.modules.loadtestservice.metrics.ServerInterval;
import com.hemz.distributedcache.modules.loadtestservice.metrics.ServerMetricsScraper;
import com.hemz.distributedcache.modules.loadtestservice.metrics.ServerSnapshot;
import com.hemz.distributedcache.modules.loadtestservice.target.TargetCacheClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Runs load tests, one at a time.
 *
 * Only one run may be active: two runs at once would compete for the same
 * service and database and ruin each other's numbers.
 */
@Service
public class RunService {

    private static final Logger log = LoggerFactory.getLogger(RunService.class);

    /** How long one load-test request may take before it counts as a timeout. */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    /** The cool-down sleep checks for "stop" this often. */
    private static final int SLEEP_STEP_MS = 100;

    private record Active(long runId, AtomicBoolean stop, Run run) {
    }

    /**
     * The target's cache when a run starts. cache: "on", "off" or "unknown".
     * backend: which cache, like "cache-system ×3" or "redis ×1", or "unknown".
     */
    private record CacheAtStart(String cache, String backend) {

        /** For the label: "on · cache-system ×3" when on, otherwise just "off" or "unknown". */
        String describe() {
            return "on".equals(cache) ? "on · " + backend : cache;
        }
    }

    private final RunStore store;
    private final SseHub hub;
    private final OpenLoopEngine engine = new OpenLoopEngine();
    private final LoadTestProperties props;
    private final TargetCacheClient targetCache;
    private final AtomicReference<Active> active = new AtomicReference<>();

    public RunService(RunStore store, SseHub hub, LoadTestProperties props, TargetCacheClient targetCache) {
        this.store = store;
        this.hub = hub;
        this.props = props;
        this.targetCache = targetCache;
    }

    public Run start(RunConfig config) {
        checkLimits(config);

        AtomicBoolean stop = new AtomicBoolean();
        Active placeholder = new Active(-1, stop, null);
        if (!active.compareAndSet(null, placeholder)) {
            Active current = active.get();
            throw new RunConflictException("Run " + (current == null ? "?" : current.runId())
                    + " is still running. Only one run at a time, so results are not contaminated.");
        }
        try {
            Run run = new Run();
            run.setId(store.nextId());
            run.setCreatedAt(Instant.now());
            run.setConfig(config);
            // Record whether Product Catalogue's cache was on, and which cache it is,
            // so every run says what it measured.
            CacheAtStart cacheAtStart = readCacheAtStart();
            String label = config.label() != null ? config.label() : defaultLabel(config);
            run.setLabel(label + " · cache " + cacheAtStart.describe());
            run.setEnvironment(environment(cacheAtStart));
            store.save(run);
            active.set(new Active(run.getId(), stop, run));
            Thread.ofPlatform().name("loadtest-run-" + run.getId()).start(() -> execute(run, stop));
            return run;
        } catch (RuntimeException e) {
            active.set(null);
            throw e;
        }
    }

    public void stop(long id) {
        Active a = active.get();
        if (a != null && a.runId() == id) {
            a.stop().set(true);
            return;
        }
        if (store.find(id).isEmpty()) {
            throw new RunNotFoundException(id);
        }
        throw new RunConflictException("Run " + id + " is not running.");
    }

    /** The live in-memory object for the active run, so late viewers see every sample. */
    public Run get(long id) {
        Active a = active.get();
        if (a != null && a.runId() == id && a.run() != null) {
            return a.run();
        }
        return store.find(id).orElseThrow(() -> new RunNotFoundException(id));
    }

    public List<RunListItem> list() {
        Active a = active.get();
        return store.findAll().stream()
                .map(r -> a != null && a.run() != null && r.getId() == a.runId() ? a.run() : r)
                .map(RunListItem::from)
                .toList();
    }

    public Optional<RunListItem> active() {
        Active a = active.get();
        return a == null || a.run() == null ? Optional.empty() : Optional.of(RunListItem.from(a.run()));
    }

    private void execute(Run run, AtomicBoolean stop) {
        long id = run.getId();
        RunConfig c = run.getConfig();
        ServerPoller poller = null;
        try {
            run.setStatus(RunStatus.RUNNING);
            run.setStartedAt(Instant.now());
            store.save(run);
            hub.publish(id, "state", RunListItem.from(run));

            log(run, "INFO", String.format(Locale.ROOT,
                    "Target %s · %s keys (1..%,d) · %d connections · %d step(s)",
                    c.target(), c.distribution(), c.maxId(), c.connections(), c.rates().size()));

            poller = new ServerPoller(new ServerMetricsScraper(c.target(), props.poolName()),
                    msg -> log(run, "WARN", msg), msg -> log(run, "INFO", msg));
            poller.start();

            int steps = c.rates().size();
            for (int i = 0; i < steps; i++) {
                if (stop.get()) {
                    break;
                }
                StepOutcome outcome = runStep(run, stop, poller, i);
                if (outcome.stopped()) {
                    break;
                }
                if (i < steps - 1) {
                    sleepUnlessStopped(RunConfig.COOLDOWN_SECONDS, stop);
                }
            }
            run.setStatus(stop.get() ? RunStatus.STOPPED : RunStatus.COMPLETED);
            log(run, stop.get() ? "WARN" : "DONE", stop.get() ? "Run stopped." : "Run complete.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            run.setStatus(RunStatus.STOPPED);
        } catch (Exception e) {
            log.error("Run {} failed", id, e);
            String reason = e.getMessage() != null ? e.getMessage() : e.toString();
            run.setStatus(RunStatus.FAILED);
            run.setFailureReason(reason);
        } finally {
            if (poller != null) {
                poller.close();
            }
            run.setFinishedAt(Instant.now());
            recordCacheAtEnd(run);
            try {
                store.save(run);
            } catch (RuntimeException e) {
                log.error("Could not save run {}", id, e);
            }
            hub.publish(id, "finished", RunListItem.from(run));
            hub.complete(id);
            active.set(null);
        }
    }

    /**
     * Runs one step (one target rate): sends the load, streams a sample every second,
     * then saves the step's summary.
     */
    private StepOutcome runStep(Run run, AtomicBoolean stop, ServerPoller poller, int stepIndex)
            throws InterruptedException {
        long id = run.getId();
        RunConfig c = run.getConfig();
        int steps = c.rates().size();
        int rate = c.rates().get(stepIndex);
        StepRecord step = new StepRecord(stepIndex, rate);
        step.setStatus("RUNNING");
        run.addStep(step);
        hub.publish(id, "step-start", Map.of("index", stepIndex, "targetRps", rate));
        log(run, "INFO", String.format(Locale.ROOT, "Step %d/%d: %,d req/s for %d s after %d s warm-up",
                stepIndex + 1, steps, rate, c.durationSeconds(), c.warmupSeconds()));

        StepSpec spec = new StepSpec(c.target(), rate, c.durationSeconds(), c.warmupSeconds(), c.connections(),
                c.distribution(), c.zipfExponent(), c.maxId(), c.seed(), REQUEST_TIMEOUT);
        StepOutcome outcome = engine.run(spec, new LoadListener() {
            @Override
            public void onInterval(IntervalStats stats) {
                Sample sample = Sample.of(stepIndex, stats, poller.latest());
                step.addSample(sample);
                hub.publish(id, "sample", sample);
            }

            @Override
            public void onLog(String level, String message) {
                log(run, level, message);
            }
        }, stop::get);

        List<Sample> measured = step.getSamples().stream().filter(s -> "measure".equals(s.phase())).toList();
        StepSummary summary = SummaryCalculator.summarize(rate, outcome, measured);
        step.setSummary(summary);
        step.setStatus(outcome.stopped() ? "STOPPED" : "DONE");
        hub.publish(id, "step", Map.of("index", stepIndex, "status", step.getStatus(), "summary", summary));
        store.save(run);
        return outcome;
    }

    private void checkLimits(RunConfig c) {
        if (c.rates().size() > props.maxSteps()) {
            throw new IllegalArgumentException("At most " + props.maxSteps() + " steps per run.");
        }
        for (int rate : c.rates()) {
            if (rate > props.maxRate()) {
                throw new IllegalArgumentException("Rate " + rate + " is above the limit of " + props.maxRate() + " req/s.");
            }
        }
        if (c.durationSeconds() > props.maxDurationSeconds()) {
            throw new IllegalArgumentException("Duration is above the limit of " + props.maxDurationSeconds() + " s.");
        }
        if (c.connections() > props.maxConnections()) {
            throw new IllegalArgumentException("Connections are above the limit of " + props.maxConnections() + ".");
        }
    }

    /**
     * Reads the target's cache once. "unknown" when Product Catalogue did not say
     * (it is down, or has no cache endpoints).
     */
    private CacheAtStart readCacheAtStart() {
        Map<String, Object> status = targetCache.status();
        String cache = status.get("enabled") instanceof Boolean on ? (on ? "on" : "off") : "unknown";
        // WHICH cache, so a Redis run and a Cache System run can be told apart and compared.
        String backend = status.get("backend") instanceof String b ? b : "unknown";
        return new CacheAtStart(cache, backend);
    }

    /** "Single 5,000 req/s" or "Sweep 2,000→16,000 req/s". The caller adds " · cache …". */
    private static String defaultLabel(RunConfig c) {
        List<Integer> r = c.rates();
        return r.size() == 1
                ? String.format(Locale.ROOT, "Single %,d req/s", r.get(0))
                : String.format(Locale.ROOT, "Sweep %,d→%,d req/s", r.get(0), r.get(r.size() - 1));
    }

    /** Which cache the run measured: "cache" (on/off) and "cacheBackend". */
    private static Map<String, Object> environment(CacheAtStart cacheAtStart) {
        Map<String, Object> env = new LinkedHashMap<>();
        env.put("cache", cacheAtStart.cache());
        env.put("cacheBackend", cacheAtStart.backend());
        return env;
    }

    /**
     * How much memory the cache used when the run ended (for the Redis comparison).
     * null for a cache-off run: the nodes may still be up, but that run never used them.
     */
    private void recordCacheAtEnd(Run run) {
        if ("off".equals(run.getEnvironment().get("cache"))) {
            run.getEnvironment().put("cacheUsedMemoryAtEnd", null);
            return;
        }
        try {
            run.getEnvironment().put("cacheUsedMemoryAtEnd", targetCache.status().get("usedMemory"));
        } catch (RuntimeException e) {
            log.warn("Run {}: could not read the cache's state at the end: {}", run.getId(), e.getMessage());
        }
    }

    /** Progress for the application log. WARN and ERROR keep their level; everything else is info. */
    private static void log(Run run, String level, String message) {
        switch (level) {
            case "WARN" -> log.warn("[run {}] {}", run.getId(), message);
            case "ERROR" -> log.error("[run {}] {}", run.getId(), message);
            default -> log.info("[run {}] {}", run.getId(), message);
        }
    }

    private static void sleepUnlessStopped(int seconds, AtomicBoolean stop) throws InterruptedException {
        int sleeps = seconds * 1000 / SLEEP_STEP_MS;
        for (int i = 0; i < sleeps && !stop.get(); i++) {
            Thread.sleep(SLEEP_STEP_MS);
        }
    }

    /** Scrapes Product Catalogue once a second, in the background, so the load generator never waits on it. */
    static final class ServerPoller implements AutoCloseable {

        private final ServerMetricsScraper scraper;
        private final Consumer<String> warn;
        private final Consumer<String> info;
        private final ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "loadtest-server-poller");
            t.setDaemon(true);
            return t;
        });
        private final AtomicReference<ServerInterval> latest = new AtomicReference<>();
        private ServerSnapshot previous;
        private boolean failing;

        ServerPoller(ServerMetricsScraper scraper, Consumer<String> warn, Consumer<String> info) {
            this.scraper = scraper;
            this.warn = warn;
            this.info = info;
        }

        void start() {
            exec.scheduleAtFixedRate(this::poll, 0, 1, TimeUnit.SECONDS);
        }

        private void poll() {
            try {
                ServerSnapshot current = scraper.scrape();
                latest.set(ServerInterval.between(previous, current));
                previous = current;
                if (failing) {
                    failing = false;
                    info.accept("Server metrics are available again.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                latest.set(null);
                if (!failing) {
                    failing = true;
                    warn.accept("Cannot read " + scraper.endpoint() + " (" + e.getClass().getSimpleName()
                            + "). Server-side charts stay empty until it recovers.");
                }
            }
        }

        ServerInterval latest() {
            return latest.get();
        }

        @Override
        public void close() {
            exec.shutdownNow();
        }
    }
}
