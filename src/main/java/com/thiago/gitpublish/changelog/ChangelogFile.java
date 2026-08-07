package com.thiago.gitpublish.changelog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ChangelogFile {

    private static final String HEADER = """
            # Changelog

            All notable changes to this project are documented in this file.

            """;

    private final Path path;

    public ChangelogFile(Path repositoryDirectory) {
        this.path = repositoryDirectory.resolve("CHANGELOG.md");
    }

    public void prepend(String section) {
        try {
            String existing = Files.exists(path)
                    ? Files.readString(path)
                    : HEADER;

            String body = existing.startsWith("# Changelog")
                    ? existing.replace(HEADER, "")
                    : existing;

            Files.writeString(path, HEADER + section + body);
        } catch (IOException e) {
            throw new IllegalStateException("Could not update " + path.toAbsolutePath(), e);
        }
    }
}
