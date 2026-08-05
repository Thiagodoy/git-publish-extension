package com.thiago.gitpublish.service;

import com.thiago.gitpublish.config.AppConfig;
import com.thiago.gitpublish.git.GitClient;
import com.thiago.gitpublish.model.SemanticVersion;
import com.thiago.gitpublish.model.TagType;
import com.thiago.gitpublish.model.VersionBump;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VersionService {

    private static final Pattern PRE_RELEASE =
            Pattern.compile("^v(\\d+)\\.(\\d+)\\.(\\d+)-(rc0|alpha0)\\.(\\d{2})$");

    private final GitClient git;

    public VersionService(GitClient git) {
        this.git = git;
    }

    public String resolveTag(String requestedValue, String branch, AppConfig config) {
        if (!isIncrement(requestedValue)) {
            return requestedValue;
        }

        VersionBump bump = VersionBump.parse(requestedValue);
        SemanticVersion latestRelease = latestStableRelease()
                .orElse(new SemanticVersion(0, 0, 0));
        SemanticVersion target = latestRelease.increment(bump);

        if (branch.equals(config.releaseBranch())) {
            return target.releaseTag();
        }

        if (branch.equals(config.releaseCandidateBranch())) {
            return nextPreRelease(target, "rc0");
        }

        return nextPreRelease(target, "alpha0");
    }

    private Optional<SemanticVersion> latestStableRelease() {
        return git.listTags().stream()
                .filter(tag -> tag.matches("^v(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)$"))
                .map(SemanticVersion::parseStableTag)
                .max(Comparator.naturalOrder());
    }

    private String nextPreRelease(SemanticVersion target, String label) {
        int next = git.listTags().stream()
                .map(PRE_RELEASE::matcher)
                .filter(Matcher::matches)
                .filter(matcher ->
                        Integer.parseInt(matcher.group(1)) == target.major()
                                && Integer.parseInt(matcher.group(2)) == target.minor()
                                && Integer.parseInt(matcher.group(3)) == target.patch()
                                && matcher.group(4).equals(label)
                )
                .mapToInt(matcher -> Integer.parseInt(matcher.group(5)))
                .max()
                .orElse(0) + 1;

        if (next > 99) {
            throw new IllegalStateException(
                    "Pre-release sequence exceeded 99 for %s-%s."
                            .formatted(target.releaseTag(), label)
            );
        }

        return "%s-%s.%02d".formatted(target.releaseTag(), label, next);
    }

    private boolean isIncrement(String value) {
        return List.of("major", "minor", "patch")
                .contains(value.toLowerCase());
    }
}
