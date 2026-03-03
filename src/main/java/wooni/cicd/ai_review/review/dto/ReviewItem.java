package wooni.cicd.ai_review.review.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ReviewItem(
        int line,
        String severity,
        String category,
        String comment,
        String suggestion
) {
}
