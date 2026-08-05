package com.thiago.gitpublish.model;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record SemanticVersion(int major, int minor, int patch) implements Comparable<SemanticVersion> {

    private static final Pattern STABLE_TAG =
            Pattern.compile("^v(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)$");

    public SemanticVersion {
        if (major < 0 || minor < 0 || patch < 0) {
            throw new IllegalArgumentException("Semantic-version components cannot be negative.");
        }
    }

    public static SemanticVersion parseStableTag(String tag) {
        Matcher matcher = STABLE_TAG.matcher(Objects.requireNonNull(tag));
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Not a stable release tag: " + tag);
        }

        return new SemanticVersion(
                Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3))
        );
    }

    public SemanticVersion increment(VersionBump bump) {
        return switch (bump) {
            case MAJOR -> new SemanticVersion(major + 1, 0, 0);
            case MINOR -> new SemanticVersion(major, minor + 1, 0);
            case PATCH -> new SemanticVersion(major, minor, patch + 1);
        };
    }

    public String releaseTag() {
        return "v%d.%d.%d".formatted(major, minor, patch);
    }

    @Override
    public int compareTo(SemanticVersion other) {
        int majorComparison = Integer.compare(major, other.major);
        if (majorComparison != 0) return majorComparison;

        int minorComparison = Integer.compare(minor, other.minor);
        if (minorComparison != 0) return minorComparison;

        return Integer.compare(patch, other.patch);
    }
}
