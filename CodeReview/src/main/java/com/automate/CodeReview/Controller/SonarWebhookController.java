package com.automate.CodeReview.Controller;


import com.automate.CodeReview.Service.SonarWebhookService;
import com.automate.CodeReview.dto.LogPayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@CrossOrigin(origins = "http://localhost:4200")
@RequestMapping("/api/sonar")
@Slf4j
public class SonarWebhookController {

    private final SonarWebhookService sonarWebhookService;

    public SonarWebhookController(SonarWebhookService sonarWebhookService) {
        this.sonarWebhookService = sonarWebhookService;
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> handle(
            @RequestBody byte[] rawBody,
            @RequestHeader(value = "X-Sonar-Webhook-HMAC-SHA256", required = false) String hmacHex,
            @RequestHeader(value = "X-SonarQube-Project", required = false) String projectKey,
            @RequestHeader(value = "X-SonarQube-Delivery", required = false) String deliveryId,
            jakarta.servlet.http.HttpServletRequest req
    ) {
        // 1) ตรวจ HMAC (ถ้าตั้ง secret)
        if (!sonarWebhookService.verifyHmac(rawBody, hmacHex)) {
            log.warn("Webhook HMAC verify failed, delivery={}, project={}", deliveryId, projectKey);
            // ถ้าอยากให้ Sonar แสดง “ส่งไม่สำเร็จ” ชัด ๆ ให้ตอบ 401
            // ถ้าไม่อยากให้ Sonar retry ซ้ำ ให้เปลี่ยนเป็น ResponseEntity.ok().build()
            return ResponseEntity.status(401).build();
        }

        // 2) parse payload ให้เร็วสุด
        var payload = sonarWebhookService.parse(rawBody);
        if (payload == null) return ResponseEntity.badRequest().build();

        // 3) ส่งไปทำงานแบบ async แล้วรีบตอบ 200 ภายใน ~10s
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
