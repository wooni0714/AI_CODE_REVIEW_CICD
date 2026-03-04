package wooni.cicd.ai_review.webhook.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RepositoryInfo(
        @JsonProperty("full_name")
        String repoFullName
) {
}
