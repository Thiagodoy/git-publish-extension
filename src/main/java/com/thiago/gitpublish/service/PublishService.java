package com.thiago.gitpublish.service;

import com.thiago.gitpublish.changelog.ChangelogFile;
import com.thiago.gitpublish.changelog.ChangelogGenerator;
import com.thiago.gitpublish.config.AppConfig;
import com.thiago.gitpublish.config.JenkinsProject;
import com.thiago.gitpublish.git.GitClient;
import com.thiago.gitpublish.jenkins.JenkinsClient;
import com.thiago.gitpublish.model.PublishContext;
import com.thiago.gitpublish.model.TagType;
import com.thiago.gitpublish.validation.TagPolicy;

public final class PublishService {

    private final GitClient git;
    private final JenkinsClient jenkins;
    private final TagPolicy tagPolicy;
    private final VersionService versionService;
    private final ChangelogGenerator changelogGenerator;
    private final ChangelogFile changelogFile;

    public PublishService(
            GitClient git,
            JenkinsClient jenkins,
            TagPolicy tagPolicy,
            VersionService versionService,
            ChangelogGenerator changelogGenerator,
            ChangelogFile changelogFile
    ) {
        this.git = git;
        this.jenkins = jenkins;
        this.tagPolicy = tagPolicy;
        this.versionService = versionService;
        this.changelogGenerator = changelogGenerator;
        this.changelogFile = changelogFile;
    }

    public PublishContext publish(
            String requestedTagOrIncrement,
            boolean dryRun,
            boolean generateChangelog,
            AppConfig config
    ) {
        git.ensureRepository();

        String branch = git.currentBranch();
        String project = git.repositoryName();

        git.ensureCleanWorkingTree();

        System.out.println("[INFO] Fetching branch and remote tags...");
        git.fetchBranchAndTags(branch);
        git.ensureBranchSynchronized(branch);

        String tag = versionService.resolveTag(requestedTagOrIncrement, branch, config);
        TagType tagType = tagPolicy.classify(tag);
        tagPolicy.validateBranch(tagType, branch, config);

        JenkinsProject jenkinsProject = config.project(project);

        if (git.localTagExists(tag)) {
            throw new IllegalStateException("Tag '%s' already exists locally.".formatted(tag));
        }
        if (git.remoteTagExists(tag)) {
            throw new IllegalStateException("Tag '%s' already exists remotely.".formatted(tag));
        }

        String changelogSection = generateChangelog && tagType.equals(TagType.RELEASE)
                ? changelogGenerator.generateSection(tag)
                : null;

        if (tagType.equals(TagType.RELEASE) && generateChangelog && !dryRun) {
            changelogFile.prepend(changelogSection);
            git.addFile("CHANGELOG.md");
            git.commit("(docs) update changelog for " + tag);
            git.pushCurrentBranch(branch);
        }

        PublishContext context = new PublishContext(
                project,
                branch,
                git.currentCommit(),
                tag,
                tagType,
                dryRun
        );

        printSummary(context, jenkinsProject, requestedTagOrIncrement, generateChangelog);

        if (dryRun) {
            System.out.println("[DRY-RUN] No Git or Jenkins write operation was executed.");
            if (generateChangelog) {
                System.out.println("[DRY-RUN] Generated changelog section:");
                System.out.println(changelogSection);
                System.out.println("[DRY-RUN] git add CHANGELOG.md");
                System.out.printf("[DRY-RUN] git commit -m \"docs: update changelog for %s\"%n", tag);
                System.out.printf("[DRY-RUN] git push origin %s%n", branch);
            }
            System.out.printf("[DRY-RUN] git tag -a %s -m \"Publish %s\"%n", tag, tag);
            System.out.printf("[DRY-RUN] git push origin refs/tags/%s%n", tag);
            System.out.println("[DRY-RUN] Jenkins body:");
            System.out.println(jenkins.payload(context));
            return context;
        }

        git.createAnnotatedTag(tag, "Publish " + tag);

        try {
            git.pushTag(tag);
        } catch (RuntimeException e) {
            git.deleteLocalTag(tag);
            throw new IllegalStateException(
                    "The tag push failed. The new local tag was removed.", e
            );
        }

        System.out.printf("[SUCCESS] Tag '%s' was created and pushed.%n", tag);

        jenkins.trigger(jenkinsProject, context);
        System.out.println("[SUCCESS] Jenkins pipeline was triggered.");

        return context;
    }

    private void printSummary(
            PublishContext context,
            JenkinsProject project,
            String requestedValue,
            boolean changelog
    ) {
        System.out.println();
        System.out.printf("[INFO] Requested:   %s%n", requestedValue);
        System.out.printf("[INFO] Project:     %s%n", context.project());
        System.out.printf("[INFO] Branch:      %s%n", context.branch());
        System.out.printf("[INFO] Commit:      %s%n", context.commit());
        System.out.printf("[INFO] Tag:         %s%n", context.tag());
        System.out.printf("[INFO] Type:        %s%n", context.tagType().displayName());
        System.out.printf("[INFO] Changelog:   %s%n", changelog ? "enabled" : "disabled");
        System.out.printf("[INFO] Jenkins URL: %s%n", project.url());
        System.out.println();
    }
}
