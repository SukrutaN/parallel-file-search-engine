package com.fileSearch.index;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Tracks which files have been indexed and when they were last modified.
 * Persisted to disk so state survives between JVM runs.
 */
public class IndexSnapshot {

    private static final Path SNAPSHOT_FILE = Path.of("index-snapshot.properties");

    // filePath -> lastModifiedMillis (as string)
    private final Map<String, Long> snapshot = new HashMap<>();

    // ── persistence ──────────────────────────────────────────────────────────

    public void load() {
        if (!Files.exists(SNAPSHOT_FILE)) return;

        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(SNAPSHOT_FILE)) {
            props.load(in);
            for (String key : props.stringPropertyNames()) {
                snapshot.put(key, Long.parseLong(props.getProperty(key)));
            }
            System.out.printf("[Snapshot] Loaded %d previously indexed files%n",
                              snapshot.size());
        } catch (IOException e) {
            System.out.println("[Snapshot] Could not load snapshot: " + e.getMessage());
        }
    }

    public void save() {
        Properties props = new Properties();
        snapshot.forEach((k, v) -> props.setProperty(k, String.valueOf(v)));

        try (OutputStream out = Files.newOutputStream(SNAPSHOT_FILE)) {
            props.store(out, "Parallel Search Engine — Index Snapshot");
            System.out.printf("[Snapshot] Saved %d entries%n", snapshot.size());
        } catch (IOException e) {
            System.out.println("[Snapshot] Could not save snapshot: " + e.getMessage());
        }
    }

    // ── query ─────────────────────────────────────────────────────────────────

    /**
     * Returns true if the file is new or has been modified since last index.
     */
    public boolean needsIndexing(Path file) {
        try {
            long diskTimestamp     = Files.getLastModifiedTime(file).toMillis();
            Long snapshotTimestamp = snapshot.get(file.toString());
            return snapshotTimestamp == null || diskTimestamp != snapshotTimestamp;
        } catch (IOException e) {
            return true; // if we can't read it, try to index it
        }
    }

    /**
     * Returns paths that were in the snapshot but are no longer on disk.
     */
    public List<String> findDeletedFiles(List<Path> currentFiles) {
        Set<String> current = new HashSet<>();
        currentFiles.forEach(p -> current.add(p.toString()));

        return snapshot.keySet().stream()
                .filter(p -> !current.contains(p))
                .toList();
    }

    // ── update ────────────────────────────────────────────────────────────────

    public void record(Path file) {
        try {
            snapshot.put(file.toString(),
                         Files.getLastModifiedTime(file).toMillis());
        } catch (IOException e) {
            snapshot.put(file.toString(), System.currentTimeMillis());
        }
    }

    public void remove(String filePath) {
        snapshot.remove(filePath);
    }

    public void clear() {
        snapshot.clear();
    }

    public int size() { return snapshot.size(); }
}