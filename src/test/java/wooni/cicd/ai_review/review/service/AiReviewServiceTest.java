package wooni.cicd.ai_review.review.service;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import wooni.cicd.ai_review.review.dto.DiffFile;
import wooni.cicd.ai_review.review.dto.ReviewResult;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AiReviewServiceTest {
    @Autowired
    private AiReviewService aiReviewService;

    @Test
    @DisplayName("문제 있는 코드 리뷰 결과 확인")
    void 문제_있는_코드_리뷰_결과_확인() {
        DiffFile diffFile = new DiffFile(
                "AiReviewTestService.java",
                """
                @@ -0,0 +1,20 @@
                +package wooni.cicd.ai_review.review_test;
                +
                +import org.apache.catalina.User;
                +import org.springframework.web.bind.annotation.RestController;
                +
                +import java.util.List;
                +
                +@RestController
                +public class AiReviewTestService {
                +
                +    public String getDisplayName(User user) {
                +        return user.getUsername().trim();
                +    }
                +
                +    public void saveAll(List<String> items) {
                +        for (String item : items) {
                +            System.out.println("saved: " + item);
                +        }
                +    }
                +}
                """,
                "added"
        );

        ReviewResult result = aiReviewService.review(diffFile).block();

        System.out.println("=== 리뷰 결과 ===");
        assertNotNull(result);
        System.out.println("reviews 건수: " + result.getReviewItems().size());
        result.getReviewItems().forEach(r ->
                System.out.printf("[%s] line=%d %s: %s%n",
                        r.severity(), r.line(), r.category(), r.comment())
        );
        System.out.println("summary: " + result.summary());
    }
}