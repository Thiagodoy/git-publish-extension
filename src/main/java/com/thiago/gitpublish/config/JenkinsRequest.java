package com.thiago.gitpublish.config;

import java.util.Map;

public record JenkinsRequest(
        String url,
        Map<String, String> headers
) {
    public JenkinsRequest {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("A Jenkins project URL cannot be empty.");
        }
        headers = headers == null ? Map.of() : Map.copyOf(headers);
    }
}
