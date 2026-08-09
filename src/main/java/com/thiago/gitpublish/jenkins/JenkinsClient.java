package com.thiago.gitpublish.jenkins;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thiago.gitpublish.config.JenkinsProject;
import com.thiago.gitpublish.model.PublishContext;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
public final class JenkinsClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final boolean isTriggerable;

    public JenkinsClient(HttpClient httpClient, boolean isTriggerable) {
        this.httpClient = httpClient;
        this.isTriggerable = isTriggerable;
    }

    public String payload(PublishContext context) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("project", context.project());
        body.put("tag", context.tag());
        body.put("tagType", context.tagType().name());
        body.put("branch", context.branch());
        body.put("commit", context.commit());

        try {
            return objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not create Jenkins request body.", e);
        }
    }

    public void trigger(JenkinsProject project, PublishContext context) {
        
        if(!isTriggerable){
            log.warn("operation= trigger, messag= Operation is disabled");
            return;
        }

        String payload = payload(context);
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(project.url()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload));

        project.headers().forEach(
                (name, value) -> requestBuilder.header(name, expandEnvironment(value))
        );

        try {
            HttpResponse<String> response = httpClient.send(
                    requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "Jenkins returned HTTP %d.%n%s"
                                .formatted(response.statusCode(), response.body())
                );
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not connect to Jenkins.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Jenkins request was interrupted.", e);
        }
    }

    private String expandEnvironment(String value) {
        if (value == null) {
            return "";
        }

        String result = value;
        int start;

        while ((start = result.indexOf("${")) >= 0) {
            int end = result.indexOf('}', start);
            if (end < 0) {
                break;
            }

            String variable = result.substring(start + 2, end);
            String environmentValue = System.getenv(variable);
            if (environmentValue == null) {
                throw new IllegalStateException(
                        "Environment variable '%s' referenced by Jenkins headers is not defined."
                                .formatted(variable)
                );
            }

            result = result.substring(0, start)
                    + environmentValue
                    + result.substring(end + 1);
        }

        return result;
    }
}
