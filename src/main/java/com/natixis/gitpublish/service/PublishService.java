package com.natixis.gitpublish.service;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import com.natixis.gitpublish.changelog.ChangelogFile;
import com.natixis.gitpublish.changelog.ChangelogGenerator;
import com.natixis.gitpublish.config.AppConfig;
import com.natixis.gitpublish.git.GitClient;
import com.natixis.gitpublish.jenkins.JenkinsClient;
import com.natixis.gitpublish.jira.JiraService;
import com.natixis.gitpublish.model.Commit;
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

        private final Predicate<Commit> filterCommits = (c) -> !c.message().contains("Merge")
                        && !c.message().contains("Release");

        public PublishService(
                        GitClient git,
                        JenkinsClient jenkins,
                        TagPolicy tagPolicy,
                        VersionService versionService,
                        ChangelogGenerator changelogGenerator,
                        ChangelogFile changelogFile,
                        JiraService jiraService) {
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
                        AppConfig config,
                        String fixBranch) {
                git.ensureRepository();

                String branch = git.currentBranch();
                String project = git.repositoryName();

                git.ensureCleanWorkingTree();

                git.fetchBranchAndTags(branch);
                git.ensureBranchSynchronized(branch);

                String tag = versionService.resolveTag(requestedTagOrIncrement, branch, config);

                TagType tagType = tagPolicy.classify(tag);
                tagPolicy.validateBranch(tagType, branch, config);

                return switch (tagType) {
                        case RELEASE ->
                                publishRelease(project, tag, branch, generateChangelog, dryRun, config, fixBranch);
                        case RELEASE_CANDIDATE -> publishReleaseCandidate(project, tag, branch, dryRun, config);
                        case ALPHA -> publishAlpha(project, tag, branch, dryRun, config);
                        default ->
                                throw new IllegalArgumentException("Unexpected value: " + tagType);
                };
        }

        private PublishContext publishAlpha(String project, String tag, String branch, boolean dryRun,
                        AppConfig config) {
                log.info("🚀 Preparing Alpha Release {}", tag);
                log.info("      🏷️  Creating Git tag {}...", tag);
                git.createAnnotatedTag(tag, "Alpha " + tag);

                git.pushTag(tag);
                log.info("      🟢 Tag created");

                PublishContext context = new PublishContext(
                                project,
                                branch,
                                git.currentCommit(),
                                tag,
                                TagType.ALPHA,
                                false);

                jenkins.trigger(config.project(project), context);

                return context;
        }

        private PublishContext publishReleaseCandidate(String project, String tag, String branch, boolean dryRun,
                        AppConfig config) {

                log.info("🚀 Preparing Release Candidate {}", tag);
                log.info("      🏷️  Creating Git tag {}...", tag);
                git.createAnnotatedTag(tag, "Release Candidate " + tag);
                git.pushTag(tag);
                log.info("      🟢 Tag created");

                PublishContext context = new PublishContext(
                                project,
                                branch,
                                git.currentCommit(),
                                tag,
                                TagType.RELEASE_CANDIDATE,
                                false);

                jenkins.trigger(config.project(project), context);

                return context;
        }

        private PublishContext publishRelease(String project,
                        String tag,
                        String branch,
                        boolean generateChangelog,
                        boolean dryRun,
                        AppConfig config,
                        String fixBranch) {

                log.info("🚀 Preparing Production Release {}", tag);

                Optional.ofNullable(fixBranch)
                                .ifPresent(v -> {

                                        git.checkout(fixBranch);
                                        git.fetchBranchAndTags(fixBranch);

                                        git.checkout("develop");
                                        git.fetchBranchAndTags("develop");
                                        git.merge(fixBranch);

                                        git.pushCurrentBranch("develop");
                                        // go back the current branch
                                        git.checkout(branch);
                                });

                git.fetchBranchAndTags("develop");

                String developCommit = git.commitOf("develop");
                String previousMainCommit = git.currentCommit();

                log.info("      📍 Main commit:    {}", previousMainCommit);
                log.info("      📍 {} commit: {}", "develop", developCommit);

                git.fetchProductionReference();

                log.info("      📝 Generating a changelog ...");

                Optional<String> productionReference = git.productionReference();

                List<Commit> commits = git.commitsBetween(
                                productionReference.orElse(null),
                                "origin/develop")
                                .stream()
                                .filter(filterCommits)
                                .toList();

                String changelogSection = changelogGenerator.generateSection(tag,
                                commits);

                log.info("      🔎 Listing all commits ...");
                commits.stream()
                                .filter(c -> !c.message().contains("Merge"))
                                .forEach(c -> log.info("                ◆ {}", c.toString()));

                git.squashMerge("origin/develop");

                changelogFile.prepend(changelogSection);
                git.addFile("CHANGELOG.md");

                if (!git.hasChangesToCommit()) {
                        throw new IllegalStateException("No changes available for production release.");
                }

                String releaseCommit = git.commitReleaseWithParents(
                                "Release " + tag,
                                previousMainCommit,
                                developCommit);

                log.info("      🏷️  Creating Git tag {}...", tag);
                // git.commitRelease(tag);

                git.createAnnotatedTag(tag, "Release " + tag);

                git.pushCurrentBranch(branch);
                git.pushTag(tag);

                log.info("      🟢 Tag created");

                git.updateProductionReference(developCommit);

                git.pushProductionReference();
                PublishContext context = new PublishContext(
                                project,
                                branch,
                                releaseCommit,
                                tag,
                                TagType.RELEASE,
                                false);

                jenkins.trigger(
                                config.project(project),
                                context);

                jiraService.createRelease(
                                tag,
                                changelogSection,
                                commits);

                return context;
        }
}
