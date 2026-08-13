package com.natixis.gitpublish.changelog;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.natixis.gitpublish.git.GitClient;
import com.natixis.gitpublish.model.Commit;

public final class ChangelogGenerator {

    private final GitClient git;


    public ChangelogGenerator(GitClient git) {
        this.git = git;
    }

    public String generateSection(String tag, List<Commit> commits) {
        
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
