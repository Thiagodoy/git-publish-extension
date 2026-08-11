package com.natixis.gitpublish.model;

public record PublishContext(
        String project,
        String branch,
        String commit,
        String tag,
        TagType tagType,
        boolean dryRun
) {
}
