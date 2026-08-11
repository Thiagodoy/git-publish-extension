package com.natixis.gitpublish.model;

import com.natixis.gitpublish.changelog.ConventionalCommitCategoryEnum;

public record Commit(String jiraIssue, String message, String hash, ConventionalCommitCategoryEnum category) {
    
}
