package wooni.cicd.ai_review.review.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ReviewResult(
        List<ReviewItem> reviewItems,
        String summary
) {
    public List<ReviewItem> getReviewItems() {
        return reviewItems != null ? reviewItems : List.of();
    }
}
