package com.fileSearch;

import com.fileSearch.index.InvertedIndex;
import com.fileSearch.model.SearchResult;
import com.fileSearch.parser.ContentParser;
import com.fileSearch.search.SearchEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class AppTest {

    private InvertedIndex index;
    private SearchEngine  engine;
    private ContentParser parser;

    @BeforeEach
    void setUp() {
        index  = new InvertedIndex();
        engine = new SearchEngine(index);
        parser = new ContentParser();

        index.addDocument("AuthService.java",
                parser.parse("public class AuthService { authentication jwt token login }"));
        index.addDocument("LoginController.java",
                parser.parse("authentication login user password controller"));
        index.addDocument("JWTProvider.java",
                parser.parse("jwt token generate validate expiry authentication"));
        index.addDocument("README.md",
                parser.parse("project readme authentication overview setup guide"));
        index.addDocument("config.json",
                parser.parse("server port timeout authentication enabled true"));
    }

    // ────────────────────────────────────────────────────────────────────────
    // Parser
    // ────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("ContentParser")
    class ParserTests {

        @Test
        @DisplayName("lowercases all tokens")
        void lowercasesTokens() {
            List<String> tokens = parser.parse("Hello WORLD Java");
            assertTrue(tokens.contains("hello"));
            assertTrue(tokens.contains("world"));
            assertTrue(tokens.contains("java"));
        }

        @Test
        @DisplayName("removes punctuation characters")
        void removesPunctuation() {
            List<String> tokens = parser.parse("hello, world! foo.bar(baz)");
            tokens.forEach(t -> assertFalse(t.matches(".*[^a-z0-9].*"),
                    "Token should not contain punctuation: " + t));
        }

        @Test
        @DisplayName("removes blank tokens from output")
        void removesBlankTokens() {
            List<String> tokens = parser.parse("  hello   world  ");
            assertFalse(tokens.contains(""));
            assertFalse(tokens.contains(" "));
        }

        @Test
        @DisplayName("handles empty string without throwing")
        void handlesEmptyString() {
            assertDoesNotThrow(() -> parser.parse(""));
            assertTrue(parser.parse("").isEmpty());
        }

        @Test
        @DisplayName("handles string with only special characters")
        void handlesOnlySpecialChars() {
            List<String> tokens = parser.parse("!@#$%^&*()");
            assertTrue(tokens.isEmpty());
        }

        @Test
        @DisplayName("splits on multiple delimiter types")
        void splitsOnMultipleDelimiters() {
            List<String> tokens = parser.parse("one-two_three/four");
            assertTrue(tokens.contains("one"));
            assertTrue(tokens.contains("two"));
            assertTrue(tokens.contains("three"));
            assertTrue(tokens.contains("four"));
        }

        @Test
        @DisplayName("preserves alphanumeric tokens like version numbers")
        void preservesAlphanumeric() {
            List<String> tokens = parser.parse("java21 version2 http2");
            assertTrue(tokens.contains("java21"));
            assertTrue(tokens.contains("version2"));
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // InvertedIndex
    // ────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("InvertedIndex")
    class IndexTests {

        @Test
        @DisplayName("returns correct frequency for indexed term")
        void returnsCorrectFrequency() {
            Map<String, Integer> matches = index.lookup("authentication");
            assertEquals(1, matches.get("AuthService.java"));
            assertEquals(1, matches.get("LoginController.java"));
        }

        @Test
        @DisplayName("returns empty map for unknown term")
        void unknownTermReturnsEmptyMap() {
            assertTrue(index.lookup("nonexistentxyz123").isEmpty());
        }

        @Test
        @DisplayName("lookup is case-insensitive")
        void caseInsensitiveLookup() {
            Map<String, Integer> lower = index.lookup("authentication");
            Map<String, Integer> upper = index.lookup("AUTHENTICATION");
            Map<String, Integer> mixed = index.lookup("Authentication");
            assertEquals(lower.keySet(), upper.keySet());
            assertEquals(lower.keySet(), mixed.keySet());
        }

        @Test
        @DisplayName("uniqueTermCount reflects all indexed tokens")
        void uniqueTermCountIsAccurate() {
            assertTrue(index.uniqueTermCount() > 0);
        }

        @Test
        @DisplayName("totalTermCount is greater than uniqueTermCount")
        void totalTermCountExceedsUnique() {
            assertTrue(index.totalTermCount() >= index.uniqueTermCount());
        }

        @Test
        @DisplayName("removeDocument evicts file from all term entries")
        void removeDocumentEvictsFile() {
            index.removeDocument("AuthService.java");
            Map<String, Integer> matches = index.lookup("authentication");
            assertFalse(matches.containsKey("AuthService.java"));
        }

        @Test
        @DisplayName("removeDocument leaves other files intact")
        void removeDocumentLeavesOthersIntact() {
            index.removeDocument("AuthService.java");
            Map<String, Integer> matches = index.lookup("authentication");
            assertTrue(matches.containsKey("LoginController.java"));
        }

        @Test
        @DisplayName("clear wipes the entire index")
        void clearWipesIndex() {
            index.clear();
            assertEquals(0, index.uniqueTermCount());
            assertEquals(0, index.totalTermCount());
        }

        @Test
        @DisplayName("addDocument accumulates frequency across calls")
        void accumulatesFrequency() {
            InvertedIndex fresh = new InvertedIndex();
            fresh.addDocument("A.java", List.of("hello", "hello", "world"));
            assertEquals(2, fresh.lookup("hello").get("A.java"));
            assertEquals(1, fresh.lookup("world").get("A.java"));
        }

        @Test
        @DisplayName("same term across multiple documents tracked separately")
        void multipleDocumentsTrackedSeparately() {
            InvertedIndex fresh = new InvertedIndex();
            fresh.addDocument("A.java", List.of("token", "token"));
            fresh.addDocument("B.java", List.of("token"));
            assertEquals(2, fresh.lookup("token").get("A.java"));
            assertEquals(1, fresh.lookup("token").get("B.java"));
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // SearchEngine — keyword
    // ────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("SearchEngine — keyword search")
    class KeywordSearchTests {

        @Test
        @DisplayName("returns non-empty results for known term")
        void returnsResultsForKnownTerm() {
            assertFalse(engine.keywordSearch("authentication").isEmpty());
        }

        @Test
        @DisplayName("returns empty list for unknown term")
        void returnsEmptyForUnknownTerm() {
            assertTrue(engine.keywordSearch("xyzzy999").isEmpty());
        }

        @Test
        @DisplayName("results are sorted by frequency descending")
        void resultsAreSortedDescending() {
            List<SearchResult> results = engine.keywordSearch("authentication");
            for (int i = 0; i < results.size() - 1; i++) {
                assertTrue(results.get(i).getFrequency() >= results.get(i + 1).getFrequency(),
                        "Results should be sorted by frequency descending");
            }
        }

        @Test
        @DisplayName("search is case-insensitive")
        void caseInsensitiveSearch() {
            List<SearchResult> lower = engine.keywordSearch("jwt");
            List<SearchResult> upper = engine.keywordSearch("JWT");
            assertEquals(lower.size(), upper.size());
        }

        @Test
        @DisplayName("SearchResult toString includes filename and match count")
        void searchResultToString() {
            List<SearchResult> results = engine.keywordSearch("authentication");
            assertFalse(results.isEmpty());
            String str = results.get(0).toString();
            assertTrue(str.contains("matches"));
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // SearchEngine — phrase search
    // ────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("SearchEngine — phrase search")
    class PhraseSearchTests {

        @Test
        @DisplayName("phrase matches only files containing all words")
        void phraseMatchesAllWords() {
            // "jwt" and "token" both appear in AuthService.java and JWTProvider.java
            List<SearchResult> results = engine.phraseSearch("jwt token");
            assertTrue(results.size() >= 1);
            results.forEach(r ->
                assertTrue(
                    index.lookup("jwt").containsKey(r.getFileName()) &&
                    index.lookup("token").containsKey(r.getFileName()),
                    "Every phrase result must contain both words"
                )
            );
        }

        @Test
        @DisplayName("phrase with no common file returns empty")
        void phraseNoMatchReturnsEmpty() {
            // "password" only in LoginController, "jwt" not in LoginController
            List<SearchResult> results = engine.phraseSearch("password jwt");
            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("single-word phrase behaves like keyword search")
        void singleWordPhraseEqualsKeyword() {
            List<SearchResult> phrase  = engine.phraseSearch("authentication");
            List<SearchResult> keyword = engine.keywordSearch("authentication");
            assertEquals(keyword.size(), phrase.size());
        }

        @Test
        @DisplayName("phrase search via search() with quoted syntax works")
        void quotedPhraseViaSearchMethod() {
            List<SearchResult> results = engine.search("\"jwt token\"");
            assertFalse(results.isEmpty());
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // SearchEngine — ext: filter
    // ────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("SearchEngine — ext: filter")
    class ExtFilterTests {

        @Test
        @DisplayName("ext:java returns only .java files")
        void extJavaReturnsOnlyJava() {
            List<SearchResult> results = engine.search("authentication ext:java");
            assertFalse(results.isEmpty());
            results.forEach(r ->
                assertTrue(r.getFileName().endsWith(".java"),
                    "Expected .java file, got: " + r.getFileName())
            );
        }

        @Test
        @DisplayName("ext:md returns only .md files")
        void extMdReturnsOnlyMd() {
            List<SearchResult> results = engine.search("authentication ext:md");
            assertFalse(results.isEmpty());
            results.forEach(r ->
                assertTrue(r.getFileName().endsWith(".md"),
                    "Expected .md file, got: " + r.getFileName())
            );
        }

        @Test
        @DisplayName("ext filter excludes non-matching extensions")
        void extFilterExcludesOtherExtensions() {
            List<SearchResult> results = engine.search("authentication ext:java");
            assertFalse(results.stream().anyMatch(r -> r.getFileName().endsWith(".md")));
            assertFalse(results.stream().anyMatch(r -> r.getFileName().endsWith(".json")));
        }

        @Test
        @DisplayName("ext filter on non-existent extension returns empty")
        void extFilterNoMatchReturnsEmpty() {
            List<SearchResult> results = engine.search("authentication ext:xyz");
            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("ext filter is case-insensitive")
        void extFilterCaseInsensitive() {
            List<SearchResult> lower = engine.search("authentication ext:java");
            List<SearchResult> upper = engine.search("authentication ext:JAVA");
            assertEquals(lower.size(), upper.size());
        }
    }
}