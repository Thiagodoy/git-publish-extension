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

import lombok.extern.slf4j.Slf4j;

@Slf4j
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

        log.info("Fetching branch and remote tags...");
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
            log.info("[DRY-RUN] No Git or Jenkins write operation was executed.");
            if (generateChangelog) {
                log.info("[DRY-RUN] Generated changelog section:");
                log.info(changelogSection);
                log.info("[DRY-RUN] git add CHANGELOG.md");
                System.out.printf("[DRY-RUN] git commit -m \"docs: update changelog for %s\"%n", tag);
                System.out.printf("[DRY-RUN] git push origin %s%n", branch);
            }
            System.out.printf("[DRY-RUN] git tag -a %s -m \"Publish %s\"%n", tag, tag);
            System.out.printf("[DRY-RUN] git push origin refs/tags/%s%n", tag);
            log.info("[DRY-RUN] Jenkins body:");
            log.info(jenkins.payload(context));
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
        log.info("[SUCCESS] Jenkins pipeline was triggered.");

        return context;
    }

    private void printSummary(
            PublishContext context,
            JenkinsProject project,
            String requestedValue,
            boolean changelog
    ) {
        
        log.info(String.format("""
            Requested:   %s
            Project:     %s
            Branch:      %s
            Commit:      %s
            Tag:         %s
            Type:        %s
            Changelog:   %s
            Jenkins URL: %s
            """, requestedValue,
            context.project(),
            context.branch(),
            context.commit(),
            context.tag(),
            context.tagType().displayName(),
            changelog ? "enabled" : "disabled",
            project.url()
        ));
    }
}
