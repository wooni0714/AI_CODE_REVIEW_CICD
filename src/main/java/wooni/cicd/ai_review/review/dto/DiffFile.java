package wooni.cicd.ai_review.review.dto;

public record DiffFile(
        String fileName,
        String patch,
        String status
) {
}
