package com.thiago.gitpublish;

import com.thiago.gitpublish.config.AppConfig;
import com.thiago.gitpublish.config.ConfigLoader;
import com.thiago.gitpublish.git.GitClient;
import com.thiago.gitpublish.git.ProcessGitClient;
import com.thiago.gitpublish.jenkins.JenkinsClient;
import com.thiago.gitpublish.changelog.ChangelogFile;
import com.thiago.gitpublish.changelog.ChangelogGenerator;
import com.thiago.gitpublish.model.PublishContext;
import com.thiago.gitpublish.model.TagType;
import com.thiago.gitpublish.service.PublishService;
import com.thiago.gitpublish.service.VersionService;
import com.thiago.gitpublish.validation.TagPolicy;

import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(
        name = "git-publish",
        mixinStandardHelpOptions = true,
        version = "git-publish 1.0.0",
        description = "Validates, creates and pushes a Git tag, then triggers the mapped Jenkins pipeline.",
        usageHelpAutoWidth = true
)
@Slf4j
public final class PublishCommand implements Callable<Integer> {

    @Parameters(
            index = "0",
            paramLabel = "TAG",
            description = {
                    "Tag to publish, or automatic increment: major, minor, patch.",
                    "Release: vX.Y.Z",
                    "Release candidate: vX.Y.Z-rc0.NN",
                    "Alpha: vX.Y.Z-alpha0.NN"
            }
    )
    private String tag;

    @Parameters(
            index = "1",
            arity = "0..1",
            paramLabel = "MODE",
            description = "Optional compatibility mode. Accepted value: dryrun."
    )
    private String positionalMode;

    @Option(
            names = {"-n", "--dry-run"},
            description = "Validate and display the operations without creating a tag, pushing, or calling Jenkins."
    )
    private boolean dryRunOption;

    @Option(
            names = {"--changelog"},
            description = "Generate or update CHANGELOG.md and commit it before tagging."
    )
    private boolean changelog;

    @Option(
            names = {"-c", "--config"},
            paramLabel = "FILE",
            description = "Configuration file. Default: ${DEFAULT-VALUE}"
    )
    private Path configFile = Path.of(System.getProperty("user.home"), ".git-publish", "config.yml");

    @Override
    public Integer call() {
        try {
            boolean dryRun = resolveDryRun();
            AppConfig config = new ConfigLoader().load(configFile);

            GitClient gitClient = new ProcessGitClient(Path.of("."));
            JenkinsClient jenkinsClient = new JenkinsClient(HttpClient.newHttpClient());
            PublishService service = new PublishService(
                    gitClient,
                    jenkinsClient,
                    new TagPolicy(),
                    new VersionService(gitClient),
                    new ChangelogGenerator(gitClient),
                    new ChangelogFile(Path.of("."))
            );

            PublishContext result = service.publish(tag, dryRun, changelog, config);

            System.out.printf("%nPublication completed for %s (%s).%n",
                    result.tag(), result.tagType().displayName());
            return 0;
        } catch (IllegalArgumentException e) {

            log.error("operation:call, message:" + e.getMessage(), e);
            return 2;
        } catch (Exception e) {
            log.error("operation:call, message:" + e.getMessage(), e);
            return 1;
        }
    }

    private boolean resolveDryRun() {
        if (positionalMode == null || positionalMode.isBlank()) {
            return dryRunOption;
        }

        if (!"dryrun".equalsIgnoreCase(positionalMode)
                && !"dry-run".equalsIgnoreCase(positionalMode)) {
            throw new IllegalArgumentException(
                    "Invalid MODE '%s'. Use 'dryrun' or the --dry-run option."
                            .formatted(positionalMode)
            );
        }

        return true;
    }
}
