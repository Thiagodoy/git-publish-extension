package com.natixis.gitpublish.jira.dto;

public record ReleaseVersionRequest(String description,
    String name,
    String projectId,
    String releaseDate,
    boolean released,
    boolean archived
) {}
    

