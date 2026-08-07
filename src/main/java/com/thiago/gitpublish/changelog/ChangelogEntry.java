package com.thiago.gitpublish.changelog;

public record ChangelogEntry(ConventionalCommitCategoryEnum category, String description, String shortCommit) {
}
