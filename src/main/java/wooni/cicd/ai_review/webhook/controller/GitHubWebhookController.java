package wooni.cicd.ai_review.webhook.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import wooni.cicd.ai_review.webhook.dto.WebhookPayload;
import wooni.cicd.ai_review.webhook.service.WebhookService;

@Slf4j
@RestController
@RequestMapping("/webhook")
@RequiredArgsConstructor
public class GitHubWebhookController {
    private final WebhookService webhookService;
    private ObjectMapper objectMapper;

    @PostMapping("/github")
    public ResponseEntity<String> createGitHubWebhook(@RequestHeader("X-Hub-Signature-256") String signature,
                                                      @RequestHeader("X-GitHub-Event") String event,
                                                      @RequestBody String body) throws JsonProcessingException {
        log.info("Webhook 수신: event={}", event);
        WebhookPayload payload = objectMapper.readValue(body, WebhookPayload.class);
        webhookService.process(signature, body, payload);
        return ResponseEntity.ok("ok");
    }
}
