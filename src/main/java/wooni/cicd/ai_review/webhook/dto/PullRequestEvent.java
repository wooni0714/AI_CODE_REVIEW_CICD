package wooni.cicd.ai_review.webhook.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PullRequestEvent(
        @JsonProperty("number")
        int number,
        @JsonProperty("head")
        Head head
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Head(
            @JsonProperty("sha")
            String sha
    ) {}

    public String getHeadSha() {
        return head != null ? head.sha() : null;
    }
}
