package com.fileSearch.index;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InvertedIndex {

    private ConcurrentHashMap<String, ConcurrentHashMap<String, Integer>> index
            = new ConcurrentHashMap<>();

    public void addDocument(String filePath, Iterable<String> tokens) {
        for (String token : tokens) {
            index
                .computeIfAbsent(token, k -> new ConcurrentHashMap<String, Integer>())
                .merge(filePath, 1, Integer::sum);
        }
    }

    public Map<String, Integer> lookup(String term) {
        ConcurrentHashMap<String, Integer> result = index.get(term.toLowerCase());
        return result != null ? result : Collections.emptyMap();
    }

    public void removeDocument(String filePath) {
        index.forEach((term, fileMap) -> fileMap.remove(filePath));
        index.entrySet().removeIf(e -> e.getValue().isEmpty());
    }

    /** Replaces the current index with a previously saved one. */
    public void restore(ConcurrentHashMap<String, ConcurrentHashMap<String, Integer>> saved) {
        this.index = saved;
    }

    /** Returns the raw map for serialization. */
    public ConcurrentHashMap<String, ConcurrentHashMap<String, Integer>> getRawIndex() {
        return index;
    }

    /** Wipes the index entirely. */
    public void clear() {
        index.clear();
    }

    public int uniqueTermCount() { return index.size(); }

    public int totalTermCount() {
        return index.values().stream()
                .mapToInt(m -> m.values().stream().mapToInt(Integer::intValue).sum())
                .sum();
    }
}