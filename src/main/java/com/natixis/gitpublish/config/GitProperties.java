package com.natixis.gitpublish.config;

public record GitProperties(
        String jenkinsWebhookUrl,
        String projectAlias
) {
    public GitProperties {
        if (jenkinsWebhookUrl == null || jenkinsWebhookUrl.isBlank()) {
            throw new IllegalArgumentException("A Jenkins project URL cannot be empty.");
        }
    }
}
