package com.natixis.gitpublish.validation;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import com.natixis.gitpublish.config.AppConfig;
import com.natixis.gitpublish.model.TagType;

public final class TagPolicy {

    private static final Pattern RELEASE =
            Pattern.compile("^v(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)$");

    private static final Pattern RELEASE_CANDIDATE =
            Pattern.compile("^v(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)-rc0\\.\\d{2}$");

    private static final Pattern ALPHA =
            Pattern.compile("^v(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)-alpha0\\.\\d{2}$");

    public TagType classify(String tag) {
        if (tag == null || tag.isBlank()) {
            throw new IllegalArgumentException("The tag cannot be empty.");
        }
        if (RELEASE.matcher(tag).matches()) {
            return TagType.RELEASE;
        }
        if (RELEASE_CANDIDATE.matcher(tag).matches()) {
            return TagType.RELEASE_CANDIDATE;
        }
        if (ALPHA.matcher(tag).matches()) {
            return TagType.ALPHA;
        }

        throw new IllegalArgumentException("""
                Invalid tag '%s'.
                Accepted formats:
                  Release:           vX.Y.Z
                  Release candidate: vX.Y.Z-rc0.NN
                  Alpha:             vX.Y.Z-alpha0.NN
                """.formatted(tag).trim());
    }

    public void validateBranch(TagType type, String branch, AppConfig config) {
        switch (type) {
            case RELEASE -> requireBranch(type, branch, config.releaseBranch());
            case RELEASE_CANDIDATE ->
                    requireBranch(type, branch, config.releaseCandidateBranch());
            case ALPHA -> {
                Set<String> forbidden = new HashSet<>();
                forbidden.add("main");
                forbidden.add("master");
                forbidden.add(config.releaseBranch());
                forbidden.add(config.releaseCandidateBranch());

                if (forbidden.contains(branch)) {
                    throw new IllegalArgumentException(
                            "Alpha tags cannot be created from branch '%s'."
                                    .formatted(branch)
                    );
                }
            }
        }
    }

    private void requireBranch(TagType type, String actual, String expected) {
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException(
                    "%s tags must be created from branch '%s'. Current branch: '%s'."
                            .formatted(capitalize(type.displayName()), expected, actual)
            );
        }
    }

    private String capitalize(String value) {
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
