package com.fileSearch.search;

import com.fileSearch.index.InvertedIndex;
import com.fileSearch.model.SearchResult;

import java.util.*;

public class SearchEngine {

    private final InvertedIndex index;

    public SearchEngine(InvertedIndex index) {
        this.index = index;
    }

    /**
     * Routes to phrase or keyword search based on query format.
     * Supports:
     *   search jwt
     *   search "authentication service"
     *   search jwt ext:java
     *   search "auth service" ext:py
     */
    public List<SearchResult> search(String rawQuery) {
        String query     = rawQuery.trim();
        String extFilter = extractExtFilter(query);

        // Strip ext:xxx from query string
        if (extFilter != null) {
            query = query.replaceAll("(?i)\\s+ext:\\S+", "").trim();
        }

        List<SearchResult> results;

        if (query.startsWith("\"") && query.endsWith("\"") && query.length() > 2) {
            // Phrase search
            String phrase = query.substring(1, query.length() - 1);
            results = phraseSearch(phrase);
        } else {
            // Single keyword search
            results = keywordSearch(query);
        }

        // Apply extension filter if present
        if (extFilter != null) {
            final String ext = "." + extFilter.toLowerCase();
            results = results.stream()
                    .filter(r -> r.getFileName().toLowerCase().endsWith(ext))
                    .toList();
        }

        return results;
    }

    // ── keyword search ───────────────────────────────────────────────────────

    public List<SearchResult> keywordSearch(String term) {
        Map<String, Integer> matches = index.lookup(term.toLowerCase());
        if (matches.isEmpty()) return Collections.emptyList();

        return matches.entrySet().stream()
                .map(e -> new SearchResult(e.getKey(), e.getValue()))
                .sorted()
                .toList();
    }

    // ── phrase search ────────────────────────────────────────────────────────
    // Strategy: intersect files that contain ALL words in the phrase,
    // then score by sum of individual word frequencies.

    public List<SearchResult> phraseSearch(String phrase) {
        String[] words = phrase.trim().toLowerCase().split("\\s+");
        if (words.length == 0) return Collections.emptyList();

        // Start with files matching the first word
        Map<String, Integer> intersection = new HashMap<>(index.lookup(words[0]));

        // Intersect with each subsequent word
        for (int i = 1; i < words.length; i++) {
            Map<String, Integer> wordFiles = index.lookup(words[i]);
            intersection.keySet().retainAll(wordFiles.keySet());
            if (intersection.isEmpty()) return Collections.emptyList();
        }

        // Score = sum of frequencies of all words in matching files
        List<SearchResult> results = new ArrayList<>();
        for (String file : intersection.keySet()) {
            int totalScore = 0;
            for (String word : words) {
                totalScore += index.lookup(word).getOrDefault(file, 0);
            }
            results.add(new SearchResult(file, totalScore));
        }

        results.sort(Comparator.naturalOrder());
        return results;
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String extractExtFilter(String query) {
        // Matches ext:java, ext:py, ext:ts etc.
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?i)\\bext:(\\w+)")
                .matcher(query);
        return m.find() ? m.group(1) : null;
    }
}