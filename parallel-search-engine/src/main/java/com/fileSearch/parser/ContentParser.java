package com.fileSearch.parser;

import java.util.Arrays;
import java.util.List;

public class ContentParser {

    /**
     * Converts raw file content into a list of normalized tokens.
     * - Lowercased
     * - Split on non-alphanumeric characters
     * - Blank tokens removed
     */
    public List<String> parse(String content) {
        String lower = content.toLowerCase();
        String[] tokens = lower.split("[^a-z0-9]+");
        return Arrays.stream(tokens)
                .filter(t -> !t.isBlank())
                .toList();
    }
}