package wooni.cicd.ai_review.review.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import wooni.cicd.ai_review.config.ReviewProperties;
import wooni.cicd.ai_review.review.dto.DiffFile;
import wooni.cicd.ai_review.review.dto.ReviewResult;

import java.time.Duration;
import java.util.Map;


@Slf4j
@Service
@RequiredArgsConstructor
public class AiReviewService {
    private final WebClient pythonAiWebClient;
    private final ReviewProperties reviewProperties;
    private final ObjectMapper objectMapper;

    public Mono<ReviewResult> review(DiffFile diffFile) {
        Map<String, Object> request = Map.of(
                "file_name", diffFile.fileName(),
                "patch",     diffFile.patch() != null ? diffFile.patch() : "",
                "status",    diffFile.status()
        );

        return pythonAiWebClient.post()
                .uri("/api/review")
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(
                                        new IllegalArgumentException("Python AI 서버 클라이언트 오류: " + body)
                                ))
                )
                .onStatus(HttpStatusCode::is5xxServerError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(
                                        new RuntimeException("Python AI 서버 오류: " + body)
                                ))
                )
                .bodyToMono(String.class)
                .map(this::parseReviewResult)
                .retryWhen(Retry.backoff(reviewProperties.maxRetry(), Duration.ofSeconds(2))
                        .filter(e -> !(e instanceof IllegalArgumentException))
                        .doBeforeRetry(signal -> log.warn("Python AI 서버 재시도: {}회차, 에러={}",
                                signal.totalRetries() + 1, signal.failure().getMessage()))
                )
                .onErrorReturn(e -> {
                    log.error("Python AI 서버 최종 실패: file={}, 에러={}",
                            diffFile.fileName(), e.getMessage());
                    return true;
                }, emptyResult());
    }

    private ReviewResult parseReviewResult(String responseBody) {
        try {
            var node = objectMapper.readTree(responseBody);
            String reviewJson = node.path("review").asText();

            String cleaned = reviewJson
                    .replaceAll("```json", "")
                    .replaceAll("```", "")
                    .trim();

            return objectMapper.readValue(cleaned, ReviewResult.class);

        } catch (Exception e) {
            log.warn("Python AI 서버 응답 파싱 실패: {}", e.getMessage());
            return emptyResult();
        }
    }

    private ReviewResult emptyResult() {
        return new ReviewResult(java.util.List.of(), "AI 리뷰 요청 실패");
    }
}