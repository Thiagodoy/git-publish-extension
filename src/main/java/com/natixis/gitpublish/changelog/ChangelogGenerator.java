package com.natixis.gitpublish.changelog;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.natixis.gitpublish.git.GitClient;
import com.natixis.gitpublish.model.Commit;
import com.natixis.gitpublish.model.SemanticVersion;

public final class ChangelogGenerator {

    private final GitClient git;
    private final Pattern RELEASE_TAG_PATTERN = Pattern.compile("^v\\d+\\.\\d+\\.\\d+$");

    public ChangelogGenerator(GitClient git) {
        this.git = git;
    }

    public String generateSection(String tag) {
        String previousTag = git.listTags().stream()
                                .filter(t-> !t.equals(tag))
                                .filter(t-> RELEASE_TAG_PATTERN.matcher(t).matches())
                                .map(t-> SemanticVersion.parseStableTag(t))
                                .max(SemanticVersion::compareTo)
                                .map(SemanticVersion::releaseTag)
                                .orElse(null);
        List<Commit> commits = git.commitSubjectsSince(previousTag);

        Map<ConventionalCommitCategoryEnum, List<ChangelogEntry>> grouped = new LinkedHashMap<>();
        grouped.put(ConventionalCommitCategoryEnum.BREAK_CHANGES, new ArrayList<>());
        grouped.put(ConventionalCommitCategoryEnum.FEATURES, new ArrayList<>());
        grouped.put(ConventionalCommitCategoryEnum.FIX, new ArrayList<>());
        grouped.put(ConventionalCommitCategoryEnum.PERFORMANCE, new ArrayList<>());
        grouped.put(ConventionalCommitCategoryEnum.DOCUMENTATION, new ArrayList<>());
        grouped.put(ConventionalCommitCategoryEnum.OTHERS, new ArrayList<>());

        for (Commit commit : commits) {
             grouped.compute(commit.category(),(key,list)->  {
                list.add(new ChangelogEntry(commit.category(),  commit.message(), commit.hash(), commit.author()));
                return list;
             });   
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
                        .append("`) ")
                        .append(entry.author() + "\n");
            }
            output.append("\n");
        }

        if (!hasEntries) {
            output.append("🟢— everything is fine, but can imply a successful action.\n\n");
        }

        return output.toString();
    }
}
