package wooni.cicd.ai_review.review.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import wooni.cicd.ai_review.config.ReviewProperties;
import wooni.cicd.ai_review.review.dto.DiffFile;
import wooni.cicd.ai_review.review.dto.ReviewResult;
import wooni.cicd.ai_review.review.util.ReviewFilter;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CodeReviewService {
    private static final String REVIEWED_COMMIT_KEY = "reviewed:commit:";

    private final GitHubApiService gitHubApiService;
    private final AiReviewService aiReviewService;
    private final ReviewFilter reviewFilter;
    private final ReviewProperties reviewProperties;
    private final ReactiveStringRedisTemplate redisTemplate;

    public Mono<Void> review(String repo, int prNumber, String commitSha) {
        return isAlreadyReviewed(commitSha)
                .flatMap(alreadyReviewed -> {
                    if (alreadyReviewed) {
                        log.info("중복 리뷰 스킵: repo={}, PR=#{}, sha={}", repo, prNumber, commitSha);
                        return Mono.empty();
                    }
                    return executeReview(repo, prNumber, commitSha);
                });
    }

    private Mono<Void> executeReview(String repo, int prNumber, String commitSha) {
        return gitHubApiService.getPrFiles(repo, prNumber)
                .flatMap(files -> {
                    List<DiffFile> targets = files.stream()
                            .filter(f -> reviewFilter.reviewTarget(f.fileName(), f.patch()))
                            .map(f -> new DiffFile(
                                    f.fileName(),
                                    reviewFilter.truncatePatch(f.patch()),
                                    f.status()
                            ))
                            .toList();

                    if (targets.isEmpty()) {
                        log.info("리뷰 대상 파일 없음: repo={}, PR=#{}", repo, prNumber);
                        return markAsReviewed(commitSha);
                    }

                    log.info("리뷰 대상 파일: {}개", targets.size());

                    return Flux.fromIterable(targets)
                            .flatMap(file -> aiReviewService.review(file)
                                    .flatMap(result -> postLineComments(repo, prNumber, commitSha, file, result))
                            )
                            .collectList()
                            .flatMap(results -> {
                                String summary = buildSummary(targets, results);
                                return gitHubApiService.postSummaryComment(repo, prNumber, summary);
                            })
                            .then(markAsReviewed(commitSha));
                })
                .doOnError(e -> log.error("코드 리뷰 실패: repo={}, PR=#{}, 원인={}",
                        repo, prNumber, e.getMessage()));
    }

    private Mono<ReviewResult> postLineComments(String repo, int prNumber, String commitSha, DiffFile file, ReviewResult result) {
        if (result.getReviewItems().isEmpty()) {
            log.info("리뷰 항목 없음: {}", file.fileName());
            return Mono.just(result);
        }

        return Flux.fromIterable(result.getReviewItems())
                .flatMap(review -> gitHubApiService.postLineComment(
                        repo, prNumber, commitSha, file.fileName(), review))
                .then(Mono.just(result));
    }

    private String buildSummary(List<DiffFile> files, List<ReviewResult> results) {
        long errorCount   = results.stream()
                .flatMap(r -> r.getReviewItems().stream())
                .filter(r -> "ERROR".equals(r.severity())).count();
        long warningCount = results.stream()
                .flatMap(r -> r.getReviewItems().stream())
                .filter(r -> "WARNING".equals(r.severity())).count();
        long infoCount    = results.stream()
                .flatMap(r -> r.getReviewItems().stream())
                .filter(r -> "INFO".equals(r.severity())).count();

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("검토 파일: %d개\n\n", files.size()));
        sb.append(String.format("🔴 ERROR: %d건 | 🟡 WARNING: %d건 | 🔵 INFO: %d건\n\n",
                errorCount, warningCount, infoCount));

        results.stream()
                .filter(r -> r.summary() != null && !r.summary().isBlank())
                .forEach(r -> sb.append("- ").append(r.summary()).append("\n"));

        return sb.toString();
    }

    private Mono<Boolean> isAlreadyReviewed(String commitSha) {
        return redisTemplate.hasKey(REVIEWED_COMMIT_KEY + commitSha);
    }

    private Mono<Void> markAsReviewed(String commitSha) {
        String key = REVIEWED_COMMIT_KEY + commitSha;
        Duration ttl = Duration.ofSeconds(reviewProperties.reviewedCommitTtl());
        return redisTemplate.opsForValue()
                .set(key, "1", ttl)
                .doOnSuccess(v -> log.info("리뷰 완료 커밋 SHA 저장: sha={}, ttl={}s",
                        commitSha, reviewProperties.reviewedCommitTtl()))
                .then();
    }
}