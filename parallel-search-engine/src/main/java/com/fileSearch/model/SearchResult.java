package com.fileSearch.model;

public class SearchResult implements Comparable<SearchResult> {
    private final String fileName;
    private final int frequency;

    public SearchResult(String fileName, int frequency) {
        this.fileName = fileName;
        this.frequency = frequency;
    }

    public String getFileName() { return fileName; }
    public int getFrequency()   { return frequency; }

    // Higher frequency = higher priority (descending sort)
    @Override
    public int compareTo(SearchResult other) {
        return Integer.compare(other.frequency, this.frequency);
    }

    @Override
    public String toString() {
        return fileName + " (" + frequency + " matches)";
    }
}