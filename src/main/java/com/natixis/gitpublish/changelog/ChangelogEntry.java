package com.natixis.gitpublish.changelog;

public record ChangelogEntry(ConventionalCommitCategoryEnum category, String description, String shortCommit) {
}
