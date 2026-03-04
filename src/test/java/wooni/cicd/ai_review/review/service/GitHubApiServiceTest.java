package wooni.cicd.ai_review.review.service;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import wooni.cicd.ai_review.review.dto.DiffFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Disabled("실제 GitHub API 호출 - 로컬에서만 실행")
class GitHubApiServiceTest {
    @Autowired
    private GitHubApiService gitHubApiService;

    @Test
    @DisplayName("PR 파일 조회")
    void PR_파일_조회_성공() {
        List<DiffFile> files = gitHubApiService.getPrFiles("wooni0714/AI_CODE_REVIEW", 6)
                .block();

        System.out.println("파일 수: " + files.size());
        files.forEach(f -> System.out.println("파일명: " + f.fileName()));
    }

    @Test
    @DisplayName("요약 리뷰 코멘트 등록")
    void 요약_리뷰_코멘트_등록_성공() {
        gitHubApiService
                .postSummaryComment("wooni0714/AI_CODE_REVIEW", 6, "테스트 요약 코멘트")
                .block();
    }
}