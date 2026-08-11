package com.natixis.gitpublish.jira;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.natixis.gitpublish.config.AppConfig;
import com.natixis.gitpublish.git.GitClient;
import com.natixis.gitpublish.jira.dto.ReleaseVersionRequest;
import com.natixis.gitpublish.jira.dto.ReleaseVersionResponse;
import com.natixis.gitpublish.model.Commit;
import com.natixis.gitpublish.model.SemanticVersion;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JiraService {

    private final static Pattern RELEASE_TAG_PATTERN = Pattern.compile("^v\\d+\\.\\d+\\.\\d+$");

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String url;
    private final boolean jiraToggle;
    private final String basicAuth;
    private final GitClient git;
    private final AppConfig config;

    public JiraService(HttpClient httpClient, boolean jiraToggle, AppConfig config, GitClient client) {
        this.httpClient = httpClient;
        this.url = config.jiraProperties().url();
        this.jiraToggle = jiraToggle;

        String credentials = config.jiraProperties().email() + ":" + config.jiraProperties().token();

        basicAuth = Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        this.git = client;
        this.config = config;

    }

    public void createRelease(String version, String description) {

        if (!jiraToggle)
            return;

        try {

            String previousTag = git.listTags().stream()
                    .filter(t -> !t.equals(version))
                    .filter(t -> RELEASE_TAG_PATTERN.matcher(t).matches())
                    .map(t -> SemanticVersion.parseStableTag(t))
                    .max(SemanticVersion::compareTo)
                    .map(SemanticVersion::releaseTag)
                    .orElse(null);
            Set<String> issues = git.commitSubjectsSince(previousTag).stream().map(Commit::jiraIssue)
                    .collect(Collectors.toSet());

            log.info("🎫 Jira issues discovered:");

            issues.stream()
                    .distinct()
                    .forEach(log::info);

            String id = createJiraReleaseVersion(version);

            linkIssuesToReleaseVersion(issues, id);

        } catch (Exception e) {
            throw new IllegalStateException("Jira process was interrupted.", e);
        }
    }

    private String createJiraReleaseVersion(String version)
            throws Exception, JsonProcessingException, JsonMappingException {
        log.info("📦 Creating Jira release {}...", version);

        String id = null;

        String projectAlias = config.project(git.repositoryName()).projectAlias();

        ReleaseVersionRequest request = new ReleaseVersionRequest("<change-request>",
                projectAlias.concat("_").concat(version),
                LocalDate.now().format(DateTimeFormatter.ISO_DATE), id, false, false);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(convertRequest(request)))
                .build();

        String response = execute(httpRequest);

        id = objectMapper.readValue(response, ReleaseVersionResponse.class).id();

        log.info("✓Jira version created: {}", id);
        return id;
    }

    private void linkIssuesToReleaseVersion(Set<String> issues, String id) throws Exception {
        log.info("🔗 Associating Jira issues...", id);

        Set<String> uniqueIssues = issues.stream().collect(Collectors.toSet());

        for (String issue : uniqueIssues) {

            var body = Map.of(
                    "update",
                    Map.of(
                            "fixVersions",
                            List.of(
                                    Map.of(
                                            "add",
                                            Map.of(
                                                    "id",
                                                    id)))));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url + "/rest/api/3/issue/" + issue))
                    .header("Authorization", "Basic " + basicAuth)
                    .PUT(HttpRequest.BodyPublishers.ofString(convertRequest(body)))
                    .build();

            execute(request);

            log.info("✓{}", issue);
        }
    }

    private String execute(HttpRequest requestBuilder) throws Exception {

        HttpResponse<String> response = httpClient.send(requestBuilder, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException(
                    "Jira returned HTTP %d.%n%s"
                            .formatted(response.statusCode(), response.body()));
        }

        return response.body();
    }

    private String convertRequest(Object request) {

        try {
            return objectMapper.writeValueAsString(request);
        } catch (Exception e) {
            throw new IllegalStateException("Could not create Jira release request body.", e);
        }
    }
}
