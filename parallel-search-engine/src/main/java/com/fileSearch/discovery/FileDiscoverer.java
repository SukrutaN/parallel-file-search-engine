package com.fileSearch.discovery;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Set;

public class FileDiscoverer {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
        ".java", ".py", ".js", ".ts", ".cpp", ".c", ".h",
        ".md", ".txt", ".json", ".xml", ".yml", ".yaml"
    );

    /**
     * Recursively finds all supported files under the given root path.
     */
    public List<Path> discover(String rootPath) throws IOException {
        Path root = Paths.get(rootPath);

        if (!Files.exists(root)) {
            throw new IllegalArgumentException("Path does not exist: " + rootPath);
        }
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Path is not a directory: " + rootPath);
        }

        try (var stream = Files.walk(root)) {
            return stream
                .filter(Files::isRegularFile)
                .filter(this::isSupported)
                .toList();
        }
    }

    private boolean isSupported(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return SUPPORTED_EXTENSIONS.stream().anyMatch(name::endsWith);
    }
}