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

        Map<String, List<ChangelogEntry>> grouped = new LinkedHashMap<>();
        grouped.put("Breaking Changes", new ArrayList<>());
        grouped.put("Features", new ArrayList<>());
        grouped.put("Bug Fixes", new ArrayList<>());
        grouped.put("Performance", new ArrayList<>());
        grouped.put("Documentation", new ArrayList<>());
        grouped.put("Maintenance", new ArrayList<>());
        grouped.put("Other Changes", new ArrayList<>());

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
        for (Map.Entry<String, List<ChangelogEntry>> group : grouped.entrySet()) {
            if (group.getValue().isEmpty()) {
                continue;
            }

            hasEntries = true;
            output.append("### ").append(group.getKey()).append("\n\n");
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
            output.append("- No user-facing changes recorded.\n\n");
        }

        return output.toString();
    }

    private ChangelogEntry classify(String subject, String shortCommit) {
        String normalized = subject.trim();
        String lower = normalized.toLowerCase();

        if (normalized.contains("!:")) {
            return entry("Breaking Changes", normalized, shortCommit);
        }
        if (lower.startsWith("(feat)")) {
            return entry("Features", stripPrefix(normalized), shortCommit);
        }
        if (lower.contains("(fix)")) {
            return entry("Bug Fixes", stripPrefix(normalized), shortCommit);
        }
        if (lower.startsWith("(perf)")) {
            return entry("Performance", stripPrefix(normalized), shortCommit);
        }
        if (lower.startsWith("(docs)") ) {
            return entry("Documentation", stripPrefix(normalized), shortCommit);
        }
        /*if (lower.startsWith("chore:")
                || lower.startsWith("chore(")
                || lower.startsWith("refactor:")
                || lower.startsWith("refactor(")
                || lower.startsWith("build:")
                || lower.startsWith("ci:")
                || lower.startsWith("test:")) {
            return entry("Maintenance", stripPrefix(normalized), shortCommit);
        }*/

        return entry("Other Changes", normalized, shortCommit);
    }

    private ChangelogEntry entry(String category, String description, String shortCommit) {
        return new ChangelogEntry(category, description, shortCommit);
    }

    private String stripPrefix(String subject) {
        int colon = subject.indexOf(':');
        return colon >= 0 ? subject.substring(colon + 1).trim() : subject;
    }
}
