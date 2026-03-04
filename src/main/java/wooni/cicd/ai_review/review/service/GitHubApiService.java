package wooni.cicd.ai_review.review.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import wooni.cicd.ai_review.review.dto.DiffFile;
import wooni.cicd.ai_review.review.dto.ReviewItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubApiService {
    private final WebClient githubWebClient;

    public Mono<List<DiffFile>> getPrFiles(String repo, int prNumber) {
        return fetchAllPages(repo, prNumber, 1, new ArrayList<>());
    }

    private Mono<List<DiffFile>> fetchAllPages(String repo, int prNumber, int page, List<DiffFile> collected) {
        return githubWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/repos/" + repo + "/pulls/" + prNumber + "/files")
                        .queryParam("per_page", 100)
                        .queryParam("page", page)
                        .build()
                )
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(
                                        new RuntimeException("GitHub API 오류: " + body)
                                ))
                        )
                .toEntity(JsonNode.class)
                .flatMap(response -> {
                    JsonNode files = response.getBody();
                    if (files == null || files.isNull()) {
                        return Mono.just(collected);
                    }

                    for (JsonNode file : files) {
                        collected.add(new DiffFile(
                                file.get("filename").asText(),
                                file.has("patch") ? file.get("patch").asText() : null,
                                file.get("status").asText()
                        ));
                    }
                    log.info("페이지 {}, 파일 수 {}, 합계 {}", page, files.size(), collected.size());

                    String linkHeader = response.getHeaders().getFirst("Link");
                    if (hasNextPage(linkHeader)) {
                        return fetchAllPages(repo, prNumber, page + 1, collected);
                    }

                    log.info("전체 파일 수집 완료: {}개", collected.size());
                    return Mono.just(collected);
                })
                .doOnError(e -> log.error("PR 파일 조회 실패 : repo: {}, PR: #{}, 에러: {}", repo, prNumber, e.getMessage()));
    }

    private boolean hasNextPage(String linkHeader) {
        return linkHeader != null && linkHeader.contains("rel=\"next\"");
    }

    public Mono<Void> postLineComment(String repo, int prNumber, String commitSha,
                                      String fileName, ReviewItem review) {
        String body = buildReviewComment(review);

        Map<String, Object> request = Map.of(
                "body", body,
                "commit_id", commitSha,
                "path", fileName,
                "line", review.line(),
                "side", "RIGHT"
        );

        return githubWebClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/repos/" + repo + "/pulls/" + prNumber + "/comments")
                        .build()
                )
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(b -> Mono.error(
                                        new IllegalArgumentException("라인 리뷰 클라이언트 오류: " + b)
                                ))
                )
                .onStatus(HttpStatusCode::is5xxServerError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(b -> Mono.error(
                                        new RuntimeException("라인 리뷰 서버 오류: " + b)
                                ))
                )
                .bodyToMono(Void.class)
                .doOnSuccess(v -> log.info("라인 리뷰 등록 완료: {} line {}",
                        fileName, review.line()))
                .onErrorResume(e -> {
                    // 라인 코멘트 실패 시 로그만 남기고 스킵
                    log.warn("라인 리뷰 실패 - 스킵: file={}, line={}, 원인={}",
                            fileName, review.line(), e.getMessage());
                    return Mono.empty();
                });
    }

    public Mono<Void> postSummaryComment(String repo, int prNumber, String summary) {
        String body = "##AI 코드 리뷰 결과\n\n" + summary;

        return githubWebClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/repos/" + repo + "/issues/" + prNumber + "/comments")
                        .build()
                )
                .bodyValue(Map.of("body", body))
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(b -> Mono.error(
                                        new IllegalArgumentException("요약 리뷰 클라이언트 오류: " + b)
                                ))
                )
                .onStatus(HttpStatusCode::is5xxServerError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(b -> Mono.error(
                                        new RuntimeException("요약 리뷰 서버 오류: " + b)
                                ))
                )
                .bodyToMono(Void.class)
                .doOnSuccess(v -> log.info("요약 리뷰 등록 완료: repo={}, PR=#{}",
                        repo, prNumber))
                .doOnError(e -> log.error("요약 리뷰 등록 실패: repo={}, PR=#{}, 원인={}",
                        repo, prNumber, e.getMessage()));
    }

    private String buildReviewComment(ReviewItem review) {
        String emoji = switch (review.severity()) {
            case "ERROR"   -> "🔴";
            case "WARNING" -> "🟡";
            default        -> "🔵";
        };

        return String.format("""
                %s **[%s]** %s
                
                %s
                
                **개선 방법**: %s
                """, emoji, review.severity(), review.category(),
                review.comment(), review.suggestion());
    }
}
