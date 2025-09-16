package com.automate.CodeReview.Controller;


import com.automate.CodeReview.service.SonarWehookService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sonar")
@Slf4j
public class SonarWebhookController {

    private final SonarWehookService sonarWehookService;

    public SonarWebhookController(SonarWehookService sonarWehookService) {
        this.sonarWehookService = sonarWehookService;
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> handle(
            @RequestBody byte[] rawBody,
            @RequestHeader(value = "X-Sonar-Webhook-HMAC-SHA256", required = false) String hmacHex,
            @RequestHeader(value = "X-SonarQube-Project", required = false) String projectKey,
            @RequestHeader(value = "X-SonarQube-Delivery", required = false) String deliveryId // บางรุ่นมี
    ) {
        if (!sonarWehookService.verifyHmac(rawBody, hmacHex)) {
            log.warn("Webhook HMAC verify failed, delivery={}, project={}", deliveryId, projectKey);
            return ResponseEntity.status(401).build();
        }

        // parse ให้เร็วที่สุด
        var payload = sonarWehookService.parse(rawBody);
        if (payload == null) return ResponseEntity.badRequest().build();

        sonarWehookService.processAsync(payload, projectKey, deliveryId);
        return ResponseEntity.ok().build();

    }
}
