package com.fileSearch.metrics;

import java.util.concurrent.atomic.AtomicLong;

public class MetricsCollector {

    private final AtomicLong filesIndexed      = new AtomicLong(0);
    private final AtomicLong totalTerms        = new AtomicLong(0);
    private final AtomicLong indexDurationMs   = new AtomicLong(0);
    private final AtomicLong lastSearchMs      = new AtomicLong(0);

    // ── writers (called from worker threads) ────────────────────────────────

    public void incrementFiles()               { filesIndexed.incrementAndGet(); }
    public void addTerms(long count)           { totalTerms.addAndGet(count); }
    public void recordIndexDuration(long ms)   { indexDurationMs.set(ms); }
    public void recordSearchLatency(long ms)   { lastSearchMs.set(ms); }

    // ── readers ─────────────────────────────────────────────────────────────

    public long getFilesIndexed()    { return filesIndexed.get(); }
    public long getTotalTerms()      { return totalTerms.get(); }
    public long getIndexDurationMs() { return indexDurationMs.get(); }
    public long getLastSearchMs()    { return lastSearchMs.get(); }

    public double getThroughput() {
        double seconds = indexDurationMs.get() / 1000.0;
        return seconds > 0 ? filesIndexed.get() / seconds : 0;
    }

    public void printReport() {
        System.out.println("─────────────────────────────");
        System.out.printf ("  Files indexed   : %d%n",        getFilesIndexed());
        System.out.printf ("  Total terms     : %d%n",        getTotalTerms());
        System.out.printf ("  Index duration  : %d ms%n",     getIndexDurationMs());
        System.out.printf ("  Throughput      : %.1f files/sec%n", getThroughput());
        System.out.printf ("  Last search     : %d ms%n",     getLastSearchMs());
        System.out.println("─────────────────────────────");
    }
}