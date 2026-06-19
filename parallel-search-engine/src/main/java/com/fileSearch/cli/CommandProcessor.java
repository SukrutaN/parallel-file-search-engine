package com.fileSearch.cli;

import com.fileSearch.discovery.FileDiscoverer;
import com.fileSearch.dispatcher.TaskDispatcher;
import com.fileSearch.index.IndexSnapshot;
import com.fileSearch.index.IndexStore;
import com.fileSearch.index.InvertedIndex;
import com.fileSearch.metrics.MetricsCollector;
import com.fileSearch.model.SearchResult;
import com.fileSearch.search.SearchEngine;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Scanner;

public class CommandProcessor {

    private static final int DEFAULT_THREADS = Runtime.getRuntime().availableProcessors();

    private final FileDiscoverer   discoverer = new FileDiscoverer();
    private final InvertedIndex    index      = new InvertedIndex();
    private final SearchEngine     engine     = new SearchEngine(index);
    private final MetricsCollector metrics    = new MetricsCollector();
    private final IndexSnapshot snapshot = new IndexSnapshot();
    private final IndexStore store = new IndexStore();

    private int threadCount = DEFAULT_THREADS;

    public void run() {
    // Load persisted index and snapshot on startup
    var savedIndex = store.load();
    if (savedIndex != null) {
        index.restore(savedIndex);
        snapshot.load();
    }
    else
    {
        snapshot.clear();
        System.out.println("[IndexStore] Snapshot reset — run 'index <path>' to rebuild.");
    }

    Scanner scanner = new Scanner(System.in);
    System.out.println("=== Parallel File Search Engine ===");
    System.out.println("Commands: index | reindex | search | stats | benchmark | save | clear | threads | help | exit");
    System.out.printf("Using %d threads by default%n%n", threadCount);

    while (true) {
        System.out.print("> ");
        String line = scanner.nextLine().trim();
        if (line.isEmpty()) continue;

        String[] parts  = line.split("\\s+", 2);
        String command  = parts[0].toLowerCase();
        String argument = parts.length > 1 ? parts[1] : "";

        switch (command) {
            case "index"     -> handleIndex(argument, threadCount, false);
            case "reindex"   -> handleIndex(argument, threadCount, true);
            case "search"    -> handleSearch(argument);
            case "stats"     -> handleStats();
            case "benchmark" -> handleBenchmark(argument);
            case "save"      -> handleSave();
            case "clear"     -> handleClear();
            case "threads"   -> handleThreads(argument);
            case "help"      -> handleHelp();
            case "exit"      -> {
                handleSave();
                System.out.println("Goodbye.");
                return;
            }
            default -> System.out.println("Unknown command: " + command);
        }
    }
}

    /**
 * @param forceReindex  if true, ignores snapshot and re-indexes everything (reindex command)
 */
private void handleIndex(String path, int threads, boolean forceReindex) {
    if (path.isEmpty()) { System.out.println("Usage: index <path>"); return; }

    System.out.println("Discovering files in: " + path);
    long start = System.currentTimeMillis();

    try {
        List<Path> allFiles = discoverer.discover(path);

        // ── remove deleted files from index ──────────────────────────────
        List<String> deleted = snapshot.findDeletedFiles(allFiles);
        if (!deleted.isEmpty()) {
            System.out.printf("Removing %d deleted file(s) from index...%n", deleted.size());
            deleted.forEach(f -> {
                index.removeDocument(f);
                snapshot.remove(f);
            });
        }

        // ── filter to only changed/new files ─────────────────────────────
        List<Path> toIndex = forceReindex
                ? allFiles
                : allFiles.stream().filter(snapshot::needsIndexing).toList();

        if (toIndex.isEmpty()) {
            if (index.uniqueTermCount() == 0) {
                // Snapshot is stale — index is empty in memory (e.g. after restart without store)
                // Force a full re-index
                toIndex = allFiles;
                System.out.printf("Rebuilding index for %d file(s) (index was empty in memory)...%n",
                                  toIndex.size());
            } else {
                System.out.println("Index is up to date. No files changed.");
                System.out.printf("(%d files already indexed)%n", allFiles.size());
                return;
            }
        } else {
            System.out.printf("%d new/modified file(s) to index (skipping %d unchanged). " +
                              "Using %d thread(s)...%n",
                              toIndex.size(), allFiles.size() - toIndex.size(), threads);
        }

        // ── evict stale data for files being re-indexed ───────────────────
        if (!forceReindex) {
            toIndex.forEach(f -> index.removeDocument(f.toString()));
        }

        TaskDispatcher dispatcher = new TaskDispatcher(threads, index, metrics, snapshot);
        dispatcher.dispatch(toIndex);

        long elapsed = System.currentTimeMillis() - start;
        metrics.recordIndexDuration(elapsed);
        snapshot.save();

        System.out.printf("Indexed %d file(s) in %d ms (%.1f files/sec)%n",
                metrics.getFilesIndexed(), elapsed, metrics.getThroughput());

    } catch (IOException | IllegalArgumentException e) {
        System.out.println("[ERROR] " + e.getMessage());
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        System.out.println("[ERROR] Indexing interrupted.");
    }
}



private void handleSave() {
    store.save(index.getRawIndex());
    snapshot.save();
    System.out.println("Index and snapshot saved.");
}

private void handleClear() {
    index.clear();
    snapshot.clear();
    store.delete();
    System.out.println("Index cleared. Run 'index <path>' to rebuild.");
}

    private void handleSearch(String query) {
    if (query.isEmpty()) { System.out.println("Usage: search <query>"); return; }

    long start = System.currentTimeMillis();
    List<SearchResult> results = engine.search(query);
    long elapsed = System.currentTimeMillis() - start;

    metrics.recordSearchLatency(elapsed);

    if (results.isEmpty()) {
        System.out.println("No results for: " + query);
    } else {
        System.out.printf("Results for %s (%d file(s)):%n", query, results.size());
        results.forEach(r -> System.out.println("  " + r));
    }
    System.out.println("Search latency: " + elapsed + " ms");
}

private void handleHelp() {
    System.out.println("""

        Commands:
          index <path>              Index new/modified files only (incremental)
          reindex <path>            Force re-index everything under path
          search <keyword>          Keyword search
          search "<phrase>"         Phrase search (AND across all words)
          search <kw> ext:<type>    Filter by file extension  e.g. ext:java
          stats                     Show metrics report
          benchmark <path>          Run 1/2/4/8 thread benchmark on a path
          save                      Manually save index to disk
          clear                     Wipe index from memory and disk
          threads <n>               Set worker thread count
          help                      Show this message
          exit                      Save and quit
        """);
}

    private void handleStats() {
        System.out.println("\n--- Metrics Report ---");
        metrics.printReport();
        System.out.printf("  Unique terms    : %d%n", index.uniqueTermCount());
        System.out.printf("  Thread count    : %d%n", threadCount);
        System.out.println("─────────────────────────────");
    }

    // Runs the same path with 1, 2, 4, 8 threads and prints a comparison table
    private void handleBenchmark(String path) {
        if (path.isEmpty()) { System.out.println("Usage: benchmark <path>"); return; }

        int[] threadCounts = {1, 2, 4, 8};
        System.out.println("\n─────────────────────────────────────────────");
        System.out.printf ("%-10s %-12s %-12s %-12s%n",
                "Threads", "Time (ms)", "Files/sec", "Speedup");
        System.out.println("─────────────────────────────────────────────");

        long baselineMs = 0;

        for (int t : threadCounts) {
            // Fresh index per run so results are comparable
            InvertedIndex    freshIndex   = new InvertedIndex();
            MetricsCollector freshMetrics = new MetricsCollector();

            try {
                List<Path> files = discoverer.discover(path);
                long start = System.currentTimeMillis();
TaskDispatcher dispatcher = new TaskDispatcher(t, freshIndex, freshMetrics, new IndexSnapshot());
                dispatcher.dispatch(files);

                long elapsed = System.currentTimeMillis() - start;
                freshMetrics.recordIndexDuration(elapsed);

                if (t == 1) baselineMs = elapsed;
                double speedup = baselineMs > 0 ? (double) baselineMs / elapsed : 1.0;

                System.out.printf("%-10d %-12d %-12.1f %-12.2fx%n",
                        t, elapsed, freshMetrics.getThroughput(), speedup);

            } catch (Exception e) {
                System.out.println("[ERROR] Benchmark failed for " + t + " threads: " + e.getMessage());
            }
        }
        System.out.println("─────────────────────────────────────────────");
    }

    private void handleThreads(String arg) {
        try {
            int n = Integer.parseInt(arg.trim());
            if (n < 1) throw new NumberFormatException();
            threadCount = n;
            System.out.println("Thread count set to " + threadCount);
        } catch (NumberFormatException e) {
            System.out.println("Usage: threads <positive integer>");
        }
    }
}