package wooni.cicd.ai_review.webhook.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WebhookPayload(
        @JsonProperty("action")
        String action,
        @JsonProperty("pull_request")
        PullRequestEvent pullRequestEvent,
        @JsonProperty("repository")
        RepositoryInfo repositoryInfo
) {}
