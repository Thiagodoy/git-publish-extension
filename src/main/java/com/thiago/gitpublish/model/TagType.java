package com.thiago.gitpublish.model;

public enum TagType {
    RELEASE("release"),
    RELEASE_CANDIDATE("release candidate"),
    ALPHA("alpha");

    private final String displayName;

    TagType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
