package com.natixis.gitpublish.git;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.natixis.gitpublish.changelog.ConventionalCommitCategoryEnum;
import com.natixis.gitpublish.model.Commit;

public final class GitClient {

    private static final Pattern JIRA_PATTERN = Pattern.compile("^([A-Z][A-Z0-9]+-\\d+):");

    private final Path workingDirectory;

    public GitClient(Path workingDirectory) {
        this.workingDirectory = workingDirectory.toAbsolutePath().normalize();
    }

    public void ensureRepository() {
        execute("rev-parse", "--is-inside-work-tree");
    }

    public String currentBranch() {
        String branch = execute("symbolic-ref", "--quiet", "--short", "HEAD").stdout();
        if (branch.isBlank()) {
            throw new IllegalStateException(
                    "Detached HEAD detected. Check out a branch before publishing.");
        }
        return branch;
    }

    public String repositoryName() {
        String remoteUrl = execute("remote", "get-url", "origin").stdout();
        String normalized = remoteUrl.replace('\\', '/');

        int separator = Math.max(normalized.lastIndexOf('/'), normalized.lastIndexOf(':'));
        String name = separator >= 0 ? normalized.substring(separator + 1) : normalized;

        if (name.endsWith(".git")) {
            name = name.substring(0, name.length() - 4);
        }

        if (name.isBlank()) {
            throw new IllegalStateException(
                    "Could not determine the project name from origin URL: " + remoteUrl);
        }
        return name;
    }

    public String currentCommit() {
        return execute("rev-parse", "HEAD").stdout();
    }

    public void ensureCleanWorkingTree() {
        String status = execute("status", "--porcelain").stdout();
        if (!status.isBlank()) {
            throw new IllegalStateException(
                    "The working tree contains uncommitted or untracked changes.");
        }
    }

    public void fetchBranchAndTags(String branch) {
        execute("fetch", "--tags", "origin", branch);
    }

    public void ensureBranchSynchronized(String branch) {
        String local = execute("rev-parse", branch).stdout();
        String remote = execute("rev-parse", "origin/" + branch).stdout();

        if (!local.equals(remote)) {
            throw new IllegalStateException(
                    "Branch '%s' is not synchronized with origin/%s."
                            .formatted(branch, branch));
        }
    }

    public boolean localTagExists(String tag) {
        return executeAllowFailure("rev-parse", "--verify", "--quiet", "refs/tags/" + tag)
                .exitCode() == 0;
    }

    public boolean remoteTagExists(String tag) {
        return executeAllowFailure(
                "ls-remote", "--exit-code", "--tags", "origin", "refs/tags/" + tag).exitCode() == 0;
    }

    public void createAnnotatedTag(String tag, String message) {
        execute("tag", "-a", tag, "-m", message);
    }

    public void pushTag(String tag) {
        execute("push", "origin", "refs/tags/" + tag);
    }

    public void deleteLocalTag(String tag) {
        executeAllowFailure("tag", "--delete", tag);
    }

    public List<String> listTags() {
        String output = execute("tag", "--list").stdout();
        return output.isBlank() ? List.of() : output.lines().toList();
    }

    public Optional<String> latestReachableTag() {
        GitResult result = executeAllowFailure("describe", "--tags", "--abbrev=0");
        return result.exitCode() == 0 && !result.stdout().isBlank()
                ? Optional.of(result.stdout())
                : Optional.empty();
    }

    public List<Commit> commitSubjectsSince(String tag) {
        String range = tag == null || tag.isBlank() ? "HEAD" : tag + "..HEAD";
        String output = execute("log", "--format=%s|%h|%an", range).stdout();

        if (output.isBlank())
            return List.of();

        return output.lines()
                .map(this::convert)
                .toList();

    }

    private Commit convert(String message) {

        String[] data = message.split("\\|");
        String hashCommit = data[1];
        String jiraIssue = extractJiraNumber(data[0]);
        ConventionalCommitCategoryEnum category = classify(data[0]);

        return new Commit(jiraIssue, data[0], hashCommit, category);

    }

    private ConventionalCommitCategoryEnum classify(String message) {

        if (message.contains("!:")) {
            return ConventionalCommitCategoryEnum.BREAK_CHANGES;
        }

        if (message.contains("(feat)")) {
            return ConventionalCommitCategoryEnum.FEATURES;
        }

        if (message.contains("(fix)")) {
            return ConventionalCommitCategoryEnum.FIX;
        }

        if (message.contains("(perf)")) {
            return ConventionalCommitCategoryEnum.PERFORMANCE;
        }

        if (message.contains("(docs)")) {
            return ConventionalCommitCategoryEnum.DOCUMENTATION;
        }

        return ConventionalCommitCategoryEnum.OTHERS;
    }

    private String extractJiraNumber(String commitMessage) {
        Matcher matcher = JIRA_PATTERN.matcher(commitMessage);

        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

    public void addFile(String path) {
        execute("add", "--", path);
    }

    public void commit(String message) {
        execute("commit", "-m", message);
    }

    public void pushCurrentBranch(String branch) {
        execute("push", "origin", branch);
    }

    private GitResult execute(String... arguments) {
        GitResult result = executeAllowFailure(arguments);
        if (result.exitCode() != 0) {
            throw new IllegalStateException(
                    "Git command failed: git %s%n%s"
                            .formatted(String.join(" ", arguments), result.stderr()));
        }
        return result;
    }

    private GitResult executeAllowFailure(String... arguments) {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(arguments));

        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(workingDirectory.toFile());

        try {
            Process process = builder.start();

            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();

            Thread stdoutReader = Thread.startVirtualThread(
                    () -> copy(process.getInputStream(), stdout));
            Thread stderrReader = Thread.startVirtualThread(
                    () -> copy(process.getErrorStream(), stderr));

            int exitCode = process.waitFor();
            stdoutReader.join();
            stderrReader.join();

            return new GitResult(
                    exitCode,
                    stdout.toString(StandardCharsets.UTF_8).trim(),
                    stderr.toString(StandardCharsets.UTF_8).trim());
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not execute Git. Confirm that Git is installed and available in PATH.",
                    e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Git execution was interrupted.", e);
        }
    }

    private static void copy(InputStream input, ByteArrayOutputStream output) {
        try (input; output) {
            input.transferTo(output);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private record GitResult(int exitCode, String stdout, String stderr) {
    }
}
