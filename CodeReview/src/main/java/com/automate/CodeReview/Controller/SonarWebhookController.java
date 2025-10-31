package com.automate.CodeReview.Controller;

import com.automate.CodeReview.Service.SonarWebhookService;
import com.automate.CodeReview.dto.LogPayload;
import com.automate.CodeReview.repository.ProjectsRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/sonar")
@Slf4j
public class SonarWebhookController {

    private final SonarWebhookService sonarWebhookService;
    private final SseController sseController;
    private final ProjectsRepository projectsRepository;

    public SonarWebhookController(
            SonarWebhookService sonarWebhookService,
            SseController sseController,
            ProjectsRepository projectsRepository
    ) {
        this.sonarWebhookService = sonarWebhookService;
        this.sseController = sseController;
        this.projectsRepository = projectsRepository;
    }

    // อันนี้เหมือนเดิม - จะเก็บไว้ก็ได้
    @PostMapping("/sonar")
    public ResponseEntity<String> webhook(@RequestBody Map<String,Object> body) {
        String projectKey = ((Map<String,Object>)body.get("project")).get("key").toString();
        return ResponseEntity.ok("ok");
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> handle(
            @RequestBody byte[] rawBody,
            @RequestHeader(value = "X-Sonar-Webhook-HMAC-SHA256", required = false) String hmacHex,
            @RequestHeader(value = "X-SonarQube-Project", required = false) String projectKeyHeader,
            @RequestHeader(value = "X-SonarQube-Delivery", required = false) String deliveryId,
            HttpServletRequest req
    ) {
        // 1) ตรวจ HMAC (ถ้าตั้ง secret)
        if (!sonarWebhookService.verifyHmac(rawBody, hmacHex)) {
            log.warn("Webhook HMAC verify failed, delivery={}, project={}", deliveryId, projectKeyHeader);
            return ResponseEntity.status(401).build();
        }

        // 2) parse payload
        var payload = sonarWebhookService.parse(rawBody);
        if (payload == null) {
            return ResponseEntity.badRequest().build();
        }

        // 2.1 ดึง projectKey จาก payload ถ้า header ไม่มี
        String projectKey = "";
        if (projectKey == null || projectKey.isBlank()) {
            // สมมติ payload มี getProject().getKey()
            if (payload.getProject() != null && payload.getProject().getKey() != null) {
                projectKey = payload.getProject().getKey();
            } else {
                projectKey = projectKeyHeader;
            }
        } else {
            projectKey = projectKeyHeader;
        }

        if (projectKey == null || projectKey.isBlank()) {
            // ยังไม่มีจริง ๆ ก็ log ไว้เฉย ๆ
            log.warn("⚠️ Sonar webhook received but projectKey is missing. delivery={}", deliveryId);
            // แล้วตอบ 200 ไปก่อน เพื่อไม่ให้ Sonar retry รัว ๆ
            return ResponseEntity.ok().build();
        }

        log.info("✅ Webhook processed: proj={}, delivery={}", projectKey, deliveryId);


        // 5) ทำงานต่อ async ตามของคุณ
        sonarWebhookService.processAsync(payload, projectKey, deliveryId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/logfile")
    public ResponseEntity<Void> updateLogFilePath(@RequestBody LogPayload payload) {
        boolean updated = sonarWebhookService.updateLogFilePath(payload);
        if (!updated) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().build();
    }
}
