package wooni.cicd.ai_review.review.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class ReviewFilterTest {
    private final ReviewFilter filter = new ReviewFilter();

    @Test
    void test_파일_제외() {
        assertThat(filter.reviewTarget(
                "src/test/java/wooni/cicd/ai_review/ProductServiceTest.java", "some patch"
        )).isFalse();
    }

    @Test
    void java가_아닌_파일_제외() {
        assertThat(filter.reviewTarget(
                "src/main/resources/application.yml", "some patch"
        )).isFalse();
    }

    @Test
    void Application_클래스_제외() {
        assertThat(filter.reviewTarget(
                "src/main/java/com/wooni/Application.java", "some patch"
        )).isFalse();
    }

    @Test
    void java_파일_성공() {
        assertThat(filter.reviewTarget(
                "src/main/java/wooni/cicd/ai_review/dto/DiffFile.java", "some patch"
        )).isTrue();
    }

    @Test
    void 변경된_코드_없는_파일_제외() {
        assertThat(filter.reviewTarget(
                "src/main/java/wooni/cicd/ai_review/dto/DiffFile.java", null
        )).isFalse();
    }
}