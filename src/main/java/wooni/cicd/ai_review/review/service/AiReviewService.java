package wooni.cicd.ai_review.review.service;

import com.fasterxml.jackson.databind.JsonNode;
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
    private static final String GEMINI_URL = "/v1beta/models/{model}:generateContent?key={apiKey}";

    private final WebClient geminiWebClient;
    private final ReviewProperties reviewProperties;
    private final ObjectMapper objectMapper;

    public Mono<ReviewResult> review(DiffFile diffFile) {
        String prompt = buildPrompt(diffFile);
        Map<String, Object> request = buildRequest(prompt);

        return geminiWebClient.post()
                .uri(GEMINI_URL, reviewProperties.aiModel(), reviewProperties.apiKey())
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(
                                        new IllegalArgumentException("Gemini API 클라이언트 오류: " + body)
                                ))
                )
                .onStatus(HttpStatusCode::is5xxServerError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(
                                        new RuntimeException("Gemini API 서버 오류: " + body)
                                ))
                )
                .bodyToMono(JsonNode.class)
                .map(this::parseReviewResult)
                .retryWhen(Retry.backoff(reviewProperties.maxRetry(), Duration.ofSeconds(2))
                        .filter(e -> !(e instanceof IllegalArgumentException)) // 4xx 재시도 제외
                        .doBeforeRetry(signal -> log.warn("Gemini API 재시도: {}회차, 에러={}",
                                signal.totalRetries() + 1, signal.failure().getMessage()))
                )
                .onErrorReturn(e -> {
                    log.error("Gemini API 최종 실패: file 이름={}, 에러={}",
                            diffFile.fileName(), e.getMessage());
                    return true;
                }, emptyResult());
    }

    private ReviewResult parseReviewResult(JsonNode response) {
        try {
            String text = response
                    .path("candidates").get(0)
                    .path("content")
                    .path("parts").get(0)
                    .path("text")
                    .asText();

            String cleaned = text
                    .replaceAll("```json", "")
                    .replaceAll("```", "")
                    .trim();

            return objectMapper.readValue(cleaned, ReviewResult.class);

        } catch (Exception e) {
            log.warn("Gemini API 응답 파싱 실패 : {}", e.getMessage());
            return emptyResult();
        }
    }

    private String buildPrompt(DiffFile diffFile) {
        return """
            당신은 BackEnd Server Java/Spring 전문 코드 리뷰어입니다.
            아래는 Git diff 형식으로 제공된 변경 내용입니다.
            
            파일명: %s
            변경 코드 (Git diff, + 추가, - 삭제, @@는 hunk 헤더):
            %s
            
            리뷰 목표:
            - 변경된 내용(+ 라인)과 그 주변 컨텍스트를 중심으로 정확한 근거가 있는 지적만 하세요.
            - 확실하지 않은 내용은 추측하지 말고 INFO로 "확인 필요"라고 표기하세요.
            
            특히 아래 항목을 중점적으로 확인하세요.
            1) NullPointerException 위험
            2) 예외 처리 누락/오용 (try-catch, 예외 전파, 로깅, 사용자 메시지)
            3) @Transactional 누락/오용 (읽기/쓰기 분리, 전파/격리, 프록시/내부호출 이슈 등)
            4) N+1 가능성 (지연로딩, 반복 조회, 페치 조인/배치 사이즈 필요 여부)
            5) 보안 이슈 (SQL Injection, 하드코딩된 민감정보, 인증/인가 누락 등)
            6) SOLID 원칙 위반/응집도 저하
            7) 불필요한 중복 코드, 불필요한 복잡도
            
            라인 번호 규칙(매우 중요):
            - "line"은 diff의 "새 파일(+)" 기준 라인 번호를 사용하세요.
            - hunk 헤더(@@ -a,b +c,d @@)의 +c 값을 기준으로, + 라인에 해당하는 라인 번호를 계산하세요.
            - 삭제(-) 라인만 있는 지적은 가장 가까운 다음 + 라인 또는 hunk의 시작(+c)에 매핑하세요.
            - 라인 번호를 계산할 수 없다면 line에 -1을 넣고 comment에 "line 계산 불가"를 적으세요.
            
            출력 형식(절대 준수):
            - 오직 "유효한 JSON"만 출력하세요. 다른 텍스트/설명/코드펜스/백틱(```), 마크다운 금지.
            - JSON은 반드시 큰따옴표(")를 사용하고, trailing comma(끝 콤마) 금지.
            
            리뷰 개수 제한:
            - reviews는 최대 10개까지만 포함하세요. (중복/사소한 스타일 지적은 제외)
            
            severity 기준:
            - ERROR: 버그/보안/데이터 정합성 등 반드시 수정 필요
            - WARNING: 잠재적 문제, 수정 권장
            - INFO: 개선 제안 또는 불확실(확인 필요)
            
            반드시 아래 JSON 스키마로만 응답하세요:
            {
              "reviews": [
                {
                  "line": 10,
                  "severity": "ERROR|WARNING|INFO",
                  "category": "NPE|EXCEPTION|TRANSACTION|NPLUS1|SECURITY|SOLID|DUPLICATION|OTHER",
                  "comment": "문제에 대한 구체적인 근거와 영향(왜 문제인지)",
                  "suggestion": "구체적인 개선 방법(가능하면 대안 1개 이상)",
                  "confidence": 0.0
                }
              ],
              "summary": "이 파일 전체에 대한 리뷰 요약 (2~3줄)"
            }
            
            규칙:
            - 문제가 없으면 reviews는 []로 두고 summary만 작성하세요.
            - comment/suggestion은 반드시 구체적으로 작성하세요(근거 없는 추상적 표현 금지).
            """.formatted(diffFile.fileName(), diffFile.status(), diffFile.patch());
    }

    private Map<String, Object> buildRequest(String prompt) {
        return Map.of(
                "contents", new Object[]{
                        Map.of("parts", new Object[]{
                                Map.of("text", prompt)
                        })
                }
        );
    }

    private ReviewResult emptyResult() {
        return new ReviewResult(java.util.List.of(), "AI 리뷰 요청 실패");
    }
}
