package com.thiago.gitpublish.model;

public record PublishContext(
        String project,
        String branch,
        String commit,
        String tag,
        TagType tagType,
        boolean dryRun
) {
}
