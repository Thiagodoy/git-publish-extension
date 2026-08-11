package com.natixis.gitpublish.model;

public enum VersionBump {
    MAJOR,
    MINOR,
    PATCH;

    public static VersionBump parse(String value) {
        return switch (value.toLowerCase()) {
            case "major" -> MAJOR;
            case "minor" -> MINOR;
            case "patch" -> PATCH;
            default -> throw new IllegalArgumentException(
                    "Unsupported version increment '%s'. Use major, minor, or patch."
                            .formatted(value)
            );
        };
    }
}
