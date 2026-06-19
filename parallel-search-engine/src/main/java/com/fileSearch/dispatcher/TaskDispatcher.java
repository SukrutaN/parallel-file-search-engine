package com.fileSearch.dispatcher;

import com.fileSearch.index.IndexSnapshot;
import com.fileSearch.index.InvertedIndex;
import com.fileSearch.metrics.MetricsCollector;
import com.fileSearch.parser.ContentParser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.*;

public class TaskDispatcher {

    private final int              threadCount;
    private final InvertedIndex    index;
    private final MetricsCollector metrics;
    private final IndexSnapshot    snapshot;
    private final ContentParser    parser = new ContentParser();

    private static final Path POISON_PILL = Path.of("__DONE__");

    public TaskDispatcher(int threadCount,
                          InvertedIndex index,
                          MetricsCollector metrics,
                          IndexSnapshot snapshot) {
        this.threadCount = threadCount;
        this.index       = index;
        this.metrics     = metrics;
        this.snapshot    = snapshot;
    }

    public void dispatch(List<Path> files) throws InterruptedException {
        BlockingQueue<Path> queue = new LinkedBlockingQueue<>(threadCount * 2);
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            pool.submit(() -> workerLoop(queue));
        }

        for (Path file : files) {
            queue.put(file);
        }
        for (int i = 0; i < threadCount; i++) {
            queue.put(POISON_PILL);
        }

        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.MINUTES);
    }

    private void workerLoop(BlockingQueue<Path> queue) {
        while (true) {
            try {
                Path file = queue.take();
                if (file.equals(POISON_PILL)) return;

                String content      = Files.readString(file);
                List<String> tokens = parser.parse(content);

                index.addDocument(file.toString(), tokens);
                snapshot.record(file);           // ← stamp this file as indexed

                metrics.incrementFiles();
                metrics.addTerms(tokens.size());

            } catch (Exception e) {
                System.out.println("  [WARN] Worker skipped: " + e.getMessage());
            }
        }
    }
}