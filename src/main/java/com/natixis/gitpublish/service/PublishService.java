package com.natixis.gitpublish.service;

import com.natixis.gitpublish.changelog.ChangelogFile;
import com.natixis.gitpublish.changelog.ChangelogGenerator;
import com.natixis.gitpublish.config.AppConfig;
import com.natixis.gitpublish.config.GitProperties;
import com.natixis.gitpublish.git.GitClient;
import com.natixis.gitpublish.jenkins.JenkinsClient;
import com.natixis.gitpublish.jira.JiraService;
import com.natixis.gitpublish.model.PublishContext;
import com.natixis.gitpublish.model.TagType;
import com.natixis.gitpublish.validation.TagPolicy;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class PublishService {

    private final GitClient git;
    private final JenkinsClient jenkins;
    private final TagPolicy tagPolicy;
    private final VersionService versionService;
    private final ChangelogGenerator changelogGenerator;
    private final ChangelogFile changelogFile;
    private final JiraService jiraService;

    public PublishService(
            GitClient git,
            JenkinsClient jenkins,
            TagPolicy tagPolicy,
            VersionService versionService,
            ChangelogGenerator changelogGenerator,
            ChangelogFile changelogFile,
            JiraService jiraService
    ) {
        this.git = git;
        this.jenkins = jenkins;
        this.tagPolicy = tagPolicy;
        this.versionService = versionService;
        this.changelogGenerator = changelogGenerator;
        this.changelogFile = changelogFile;
        this.jiraService = jiraService;
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

        git.fetchBranchAndTags(branch);
        git.ensureBranchSynchronized(branch);

        String tag = versionService.resolveTag(requestedTagOrIncrement, branch, config);

        log.info("🚀 Preparing release " + tag);

        TagType tagType = tagPolicy.classify(tag);
        tagPolicy.validateBranch(tagType, branch, config);

        GitProperties gitProperties = config.project(project);

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

        if (dryRun) {
            log.info("[DRY-RUN] No Git or Jenkins write operation was executed.");
            if (generateChangelog) {
                log.info("[DRY-RUN] Generated changelog section:");
                log.info(changelogSection);
                log.info("[DRY-RUN] git add CHANGELOG.md");
                log.info("[DRY-RUN] git commit -m \"docs: update changelog for {}\"\n", tag);
                log.info("[DRY-RUN] git push origin {}\n", branch);
            }
            log.info("[DRY-RUN] git tag -a {} -m \"Publish {}\"\n", tag, tag);
            log.info("[DRY-RUN] git push origin refs/tags/{}\n", tag);
            log.info("[DRY-RUN] Jenkins body:");
            log.info(jenkins.payload(context));
            return context;
        }

        log.info("🏷️  Creating Git tag {}...", tag);
        git.createAnnotatedTag(tag, "Publish " + tag);

        try {
            git.pushTag(tag);
            log.info("✓ Tag created");
        } catch (RuntimeException e) {
            git.deleteLocalTag(tag);
            throw new IllegalStateException(
                    "The tag push failed. The new local tag was removed.", e
            );
        }

        jenkins.trigger(gitProperties, context);
        jiraService.createRelease(tag, changelogSection);
        
        return context;
    }    
}
