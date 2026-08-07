package com.thiago.gitpublish.changelog;

public enum ConventionalCommitCategoryEnum {

    BREAK_CHANGES("💥 Breaking Changes"),

    FEATURES("✨ Features"),

    FIX("🐛 Bug Fixes"),

    PERFORMANCE("⚡ Performance"),

    DOCUMENTATION("📚 Documentation"),

    OTHERS("🔄 Other Changes");

    private String value;

    ConventionalCommitCategoryEnum(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

}
