package com.thiago.gitpublish.changelog;

import com.thiago.gitpublish.git.GitClient;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ChangelogGenerator {

    private final GitClient git;

    public ChangelogGenerator(GitClient git) {
        this.git = git;
    }

    public String generateSection(String tag) {
        String previousTag = git.latestReachableTag().orElse(null);
        List<String> commits = git.commitSubjectsSince(previousTag);

        Map<ConventionalCommitCategoryEnum, List<ChangelogEntry>> grouped = new LinkedHashMap<>();
        grouped.put(ConventionalCommitCategoryEnum.BREAK_CHANGES, new ArrayList<>());
        grouped.put(ConventionalCommitCategoryEnum.FEATURES, new ArrayList<>());
        grouped.put(ConventionalCommitCategoryEnum.FIX, new ArrayList<>());
        grouped.put(ConventionalCommitCategoryEnum.PERFORMANCE, new ArrayList<>());
        grouped.put(ConventionalCommitCategoryEnum.DOCUMENTATION, new ArrayList<>());
        grouped.put(ConventionalCommitCategoryEnum.OTHERS, new ArrayList<>());

        for (String commit : commits) {
            String[] parts = commit.split("\\|", 2);
            String shortCommit = parts[0];
            String subject = parts.length > 1 ? parts[1].trim() : commit.trim();

            ChangelogEntry entry = classify(subject, shortCommit);
            grouped.get(entry.category()).add(entry);
        }

        StringBuilder output = new StringBuilder();
        output.append("## [").append(tag).append("] - ").append(LocalDate.now()).append("\n\n");

        boolean hasEntries = false;
        for (Map.Entry<ConventionalCommitCategoryEnum, List<ChangelogEntry>> group : grouped.entrySet()) {
            if (group.getValue().isEmpty()) {
                continue;
            }

            hasEntries = true;
            output.append("### ").append(group.getKey().getValue()).append("\n\n");
            for (ChangelogEntry entry : group.getValue()) {
                output.append("- ")
                        .append(entry.description())
                        .append(" (`")
                        .append(entry.shortCommit())
                        .append("`)\n");
            }
            output.append("\n");
        }

        if (!hasEntries) {
            output.append("✅ — everything is fine, but can imply a successful action.\n\n");
        }

        return output.toString();
    }

    private ChangelogEntry classify(String subject, String shortCommit) {
        String normalized = subject.trim();
        String lower = normalized.toLowerCase();

        if (normalized.contains("!:")) {
            return entry(ConventionalCommitCategoryEnum.BREAK_CHANGES, normalized, shortCommit);
        }

        if (lower.startsWith("(feat)")) {
            return entry(ConventionalCommitCategoryEnum.FEATURES, stripPrefix(normalized), shortCommit);
        }

        if (lower.contains("(fix)")) {
            return entry(ConventionalCommitCategoryEnum.FIX, stripPrefix(normalized), shortCommit);
        }

        if (lower.startsWith("(perf)")) {
            return entry(ConventionalCommitCategoryEnum.PERFORMANCE, stripPrefix(normalized), shortCommit);
        }

        if (lower.startsWith("(docs)")) {
            return entry(ConventionalCommitCategoryEnum.DOCUMENTATION, stripPrefix(normalized), shortCommit);
        }

        return entry(ConventionalCommitCategoryEnum.OTHERS, normalized, shortCommit);
    }

    private ChangelogEntry entry(ConventionalCommitCategoryEnum category, String description, String shortCommit) {
        return new ChangelogEntry(category, description, shortCommit);
    }

    private String stripPrefix(String subject) {
        int colon = subject.indexOf(':');
        return colon >= 0 ? subject.substring(colon + 1).trim() : subject;
    }
}
