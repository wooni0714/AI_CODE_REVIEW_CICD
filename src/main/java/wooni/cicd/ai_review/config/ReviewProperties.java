package wooni.cicd.ai_review.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "review")
public record ReviewProperties(
    String apiKey,
    String githubToken,
    String webhookSecret,
    @DefaultValue("gemini-2.5-flash")
    String aiModel,
    @DefaultValue("3")
    int maxRetry,
    @DefaultValue("86400")
    long reviewedCommitTtl
) {}
