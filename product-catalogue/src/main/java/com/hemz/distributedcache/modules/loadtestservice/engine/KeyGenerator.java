package com.hemz.distributedcache.modules.loadtestservice.engine;

import java.util.Arrays;
import java.util.SplittableRandom;

/**
 * Chooses which product id each request asks for.
 *
 * Popular "ranks" are mapped to ids with a fixed permutation, so:
 *  - hot rows are spread across the table instead of sitting on the same disk pages
 *    (which would make the database look faster than it really is), and
 *  - the same seed always produces the same hot set, so two runs are comparable.
 *
 * Not thread-safe. The dispatcher thread owns one instance.
 */
public final class KeyGenerator {

    static final long BASE_MULTIPLIER = 7919L;   // a prime; nudged until coprime with maxId
    static final long OFFSET = 12_345L;

    private final KeyDistribution distribution;
    private final long maxId;
    private final SplittableRandom random;
    private final long multiplier;
    private final long offset;
    private final double[] zipfCdf;

    public KeyGenerator(KeyDistribution distribution, long maxId, double zipfExponent, long seed) {
        if (maxId < 1) {
            throw new IllegalArgumentException("maxId must be at least 1");
        }
        if (maxId > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("maxId too large");
        }
        this.distribution = distribution;
        this.maxId = maxId;
        this.random = new SplittableRandom(seed);
        this.multiplier = coprimeMultiplier(maxId);
        this.offset = OFFSET % maxId;
        this.zipfCdf = distribution == KeyDistribution.ZIPFIAN ? buildZipfCdf((int) maxId, zipfExponent) : null;
    }

    public long next() {
        return switch (distribution) {
            case UNIFORM -> 1 + random.nextLong(maxId);
            case ZIPFIAN -> idForRank(zipfRank(random.nextDouble()));
            case HOTSPOT -> {
                long hotSetSize = Math.max(1, maxId / 100);            // 1% of the ids
                yield random.nextDouble() < 0.9
                        ? idForRank(random.nextLong(hotSetSize))      // get 90% of the traffic
                        : 1 + random.nextLong(maxId);
            }
        };
    }

    /** Rank 0 is the most popular. Bijective: every rank maps to exactly one id in 1..maxId. */
    public long idForRank(long rank) {
        return Math.floorMod(rank * multiplier + offset, maxId) + 1;
    }

    private long zipfRank(double u) {
        int i = Arrays.binarySearch(zipfCdf, u);
        if (i < 0) {
            i = -i - 1;
        }
        return Math.min(i, zipfCdf.length - 1);
    }

    static double[] buildZipfCdf(int n, double s) {
        double[] cdf = new double[n];
        double sum = 0;
        for (int k = 1; k <= n; k++) {
            sum += 1.0 / Math.pow(k, s);
            cdf[k - 1] = sum;
        }
        for (int i = 0; i < n; i++) {
            cdf[i] /= sum;
        }
        cdf[n - 1] = 1.0;
        return cdf;
    }

    static long coprimeMultiplier(long maxId) {
        long m = BASE_MULTIPLIER;
        while (gcd(m, maxId) != 1) {
            m++;
        }
        return m;
    }

    static long gcd(long a, long b) {
        while (b != 0) {
            long t = a % b;
            a = b;
            b = t;
        }
        return Math.abs(a);
    }
}
