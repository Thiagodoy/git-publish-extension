package com.thiago.gitpublish;

import picocli.CommandLine;

public final class GitPublishApplication {

    private GitPublishApplication() {
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new PublishCommand()).execute(args);
        System.exit(exitCode);
    }
}
