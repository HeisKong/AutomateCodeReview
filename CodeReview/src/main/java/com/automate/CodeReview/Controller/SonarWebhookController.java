package com.automate.CodeReview.Controller;

import com.automate.CodeReview.Service.SonarSyncService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SonarWebhookController {
    private final SonarSyncService syncService;

    @PostMapping("/sonar/webhook")
    public ResponseEntity<Void> onWebhook(@RequestBody JsonNode payload) {
        // payload มี project.key, qualityGate.status, taskId, status=SUCCESS/FAILED
        String projectKey = payload.path("project").path("key").asText();
        // คุณจะทำ sync เฉพาะโปรเจ็กต์นี้ก็ได้:
        // syncService.syncOne(projectKey, payload.path("qualityGate").path("status").asText());
        syncService.syncAllProjects(); // ตัวอย่างง่ายสุด
        return ResponseEntity.ok().build();
    }
}

