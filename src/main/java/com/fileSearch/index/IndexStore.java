package com.fileSearch.index;

import java.io.*;
import java.nio.file.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Saves and loads the full inverted index to/from disk.
 * Uses Java serialization — fast and zero dependencies.
 */
public class IndexStore {

    private static final Path STORE_FILE = Path.of("index-store.ser");

    /**
     * Persists the index map to disk.
     */
    public void save(ConcurrentHashMap<String, ConcurrentHashMap<String, Integer>> index) {
        try (ObjectOutputStream out = new ObjectOutputStream(
                new BufferedOutputStream(Files.newOutputStream(STORE_FILE)))) {
            out.writeObject(index);
            System.out.printf("[IndexStore] Saved %d terms to %s%n",
                              index.size(), STORE_FILE.toAbsolutePath());
        } catch (IOException e) {
            System.out.println("[IndexStore] Failed to save: " + e.getMessage());
        }
    }

    /**
     * Loads the index map from disk.
     * Returns null if no store file exists or loading fails.
     */
    @SuppressWarnings("unchecked")
    public ConcurrentHashMap<String, ConcurrentHashMap<String, Integer>> load() {
        if (!Files.exists(STORE_FILE)) {
            System.out.println("[IndexStore] No existing index found — starting fresh.");
            return null;
        }

        try (ObjectInputStream in = new ObjectInputStream(
                new BufferedInputStream(Files.newInputStream(STORE_FILE)))) {
            var index = (ConcurrentHashMap<String, ConcurrentHashMap<String, Integer>>) in.readObject();
            System.out.printf("[IndexStore] Loaded %d terms from %s%n",
                              index.size(), STORE_FILE.toAbsolutePath());
            return index;
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("[IndexStore] Failed to load index: " + e.getMessage());
            return null;
        }
    }

    public boolean exists() {
        return Files.exists(STORE_FILE);
    }

    public void delete() {
        try {
            Files.deleteIfExists(STORE_FILE);
            System.out.println("[IndexStore] Index store deleted.");
        } catch (IOException e) {
            System.out.println("[IndexStore] Could not delete store: " + e.getMessage());
        }
    }
}