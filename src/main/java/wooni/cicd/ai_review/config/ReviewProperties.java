package wooni.cicd.ai_review.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "review")
public record ReviewProperties(
    String githubToken,
    String webhookSecret,
    @DefaultValue("3")
    int maxRetry,
    @DefaultValue("86400")
    long reviewedCommitTtl,
    @DefaultValue("http://python-ai:8000")
    String pythonAiUrl
) {}
