package wooni.cicd.ai_review.webhook.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import wooni.cicd.ai_review.config.ReviewProperties;
import wooni.cicd.ai_review.review.service.CodeReviewService;
import wooni.cicd.ai_review.webhook.dto.WebhookPayload;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {
    private static final Set<String> REVIEW_ACTIONS = Set.of("opened", "synchronize");
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final ReviewProperties reviewProperties;
    private final CodeReviewService codeReviewService;

    public void process(String signature, String rawBody, WebhookPayload payload) {
        validateSignature(signature, rawBody);

        String action = payload.action();
        if (REVIEW_ACTIONS.contains(action)) {
            log.info("리뷰 대상 아님 {}", action);
            return;
        }

        if (payload.pullRequestEvent() == null || payload.repositoryInfo() == null) {
            log.warn("PR 또는 레포 정보 없음");
            return;
        }

        String repo      = payload.repositoryInfo().repoFullName();
        int    prNumber  = payload.pullRequestEvent().number();
        String commitSha = payload.pullRequestEvent().getHeadSha();

        log.info("코드 리뷰 시작: repo={}, PR=#{}, sha={}", repo, prNumber, commitSha);

        codeReviewService.review(repo, prNumber, commitSha)
                .subscribe(
                        null,
                        e -> log.error("코드 리뷰 실패: repo={}, PR=#{}, 에러 원인={}", repo, prNumber, e.getMessage())
                );
}

    private void validateSignature(String signature, String rawBody) {
        if (signature == null || !signature.startsWith("sha256=")) {
            throw new SecurityException("Webhook 오류");
        }

        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(
                    reviewProperties.webhookSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM
            ));

            byte[] hash = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            String expectedHash = "sha256=" + HexFormat.of().formatHex(hash);

            if (!expectedHash.equals(signature)) {
                throw new SecurityException("Webhook 서명 불일치");
            }
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new SecurityException("Webhook 서명 검증 오류: " + e.getMessage());
        }
    }
}
