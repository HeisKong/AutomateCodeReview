package com.automate.CodeReview.Controller;

import com.automate.CodeReview.Service.SonarWehookService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@CrossOrigin(origins = "http://localhost:4200")
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
            @RequestHeader(value = "X-SonarQube-Delivery", required = false) String deliveryId,
            jakarta.servlet.http.HttpServletRequest req
    ) {
        // 1) ตรวจ HMAC (ถ้าตั้ง secret)
        if (!sonarWehookService.verifyHmac(rawBody, hmacHex)) {
            log.warn("Webhook HMAC verify failed, delivery={}, project={}", deliveryId, projectKey);
            // ถ้าอยากให้ Sonar แสดง “ส่งไม่สำเร็จ” ชัด ๆ ให้ตอบ 401
            // ถ้าไม่อยากให้ Sonar retry ซ้ำ ให้เปลี่ยนเป็น ResponseEntity.ok().build()
            return ResponseEntity.status(401).build();
        }
        log.info(">> {} {} delivery={} project={}", req.getMethod(), req.getRequestURI(), deliveryId, projectKey);
        log.info(">> {} {} from={} delivery={} project={}",
                req.getMethod(), req.getRequestURI(), req.getRemoteAddr(), deliveryId, projectKey);

        // 2) parse payload ให้เร็วสุด
        var payload = sonarWehookService.parse(rawBody);
        if (payload == null) return ResponseEntity.badRequest().build();

        // 3) ส่งไปทำงานแบบ async แล้วรีบตอบ 200 ภายใน ~10s
        sonarWehookService.processAsync(payload, projectKey, deliveryId);
        return ResponseEntity.ok().build();
    }
}
