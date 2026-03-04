package wooni.cicd.ai_review.review.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class ReviewFilter {
    private static final List<String> EXCLUDE_PATHS =  List.of(
            "/test/",
            "generated/",
            "build/"
    );

    private static final List<String> EXCLUDE_FILENAME_SUFFIXES =  List.of(
            "AiReviewerApplication.java",
            "ReviewProperties.java",
            "WebClientConfig.java",
            "Config.java",
            "Application.java"
    );

    private static final String QUERYDSL_PREFIX = "/Q";
    private static final int MAX_PATCH_LENGTH = 3000;

    public boolean reviewTarget(String fileName, String patch) {
        if (!fileName.endsWith(".java")) {
            log.info("java파일 아님 fileName : {}", fileName);
            return false;
        }

        for (String excludePath : EXCLUDE_PATHS) {
            if (fileName.contains(excludePath)) {
                log.info("제외 경로 : excludePath : {},fileName : {}", excludePath, fileName);
                return false;
            }
        }

        if (fileName.contains(QUERYDSL_PREFIX)) {
            log.info("Q Class 제외 fileName : {}", fileName);
            return false;
        }

        String simpleFilename = fileName.substring(fileName.lastIndexOf("/") + 1);
        for (String excludedFileName : EXCLUDE_FILENAME_SUFFIXES) {
            if (simpleFilename.endsWith(excludedFileName)) {
                log.info("제외 파일명 fileName : {}", fileName);
                return false;
            }
        }

        if ((patch == null || patch.isBlank())) {
            log.info("patch(변경된 코드) 없음 fileName : {}", fileName);
            return false;
        }

        return true;
    }

    public String truncatePatch(String patch) {
        if (patch.length() <= MAX_PATCH_LENGTH) {
            return patch;
        }
        log.warn("patch 길이 초과 {}자를 {}자로 자르기", patch.length(), MAX_PATCH_LENGTH);
        return patch.substring(0, MAX_PATCH_LENGTH);
    }
}