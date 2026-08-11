package com.natixis.gitpublish.config;

import java.util.Map;

public record AppConfig(
        String releaseBranch,
        String releaseCandidateBranch,
        JiraProperties jiraProperties,
        Map<String, GitProperties> projects
) {
    public AppConfig {
        releaseBranch = blankToDefault(releaseBranch, "main");
        releaseCandidateBranch = blankToDefault(releaseCandidateBranch, "develop");
        projects = projects == null ? Map.of() : Map.copyOf(projects);
    }

    private static String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    public GitProperties project(String repositoryName) {
        GitProperties project = projects.get(repositoryName);
        if (project == null) {
            throw new IllegalArgumentException(
                    "No Jenkins mapping exists for repository '%s'. Add it to the projects section."
                            .formatted(repositoryName)
            );
        }
        return project;
    }
}
