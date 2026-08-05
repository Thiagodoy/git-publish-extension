package com.thiago.gitpublish.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SemanticVersionTest {

    @Test
    void incrementsMajor() {
        assertEquals("v2.0.0",
                new SemanticVersion(1, 8, 4).increment(VersionBump.MAJOR).releaseTag());
    }

    @Test
    void incrementsMinor() {
        assertEquals("v1.9.0",
                new SemanticVersion(1, 8, 4).increment(VersionBump.MINOR).releaseTag());
    }

    @Test
    void incrementsPatch() {
        assertEquals("v1.8.5",
                new SemanticVersion(1, 8, 4).increment(VersionBump.PATCH).releaseTag());
    }
}
