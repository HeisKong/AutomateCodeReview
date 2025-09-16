package com.automate.CodeReview.Controller;

import com.automate.CodeReview.service.OwaspService;
import com.automate.CodeReview.service.SonarTokenService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sonar")
@RequiredArgsConstructor
public class OwaspController {

    private final OwaspService service;
    private final SonarTokenService tokenService;
    private final ObjectMapper mapper;

    @PostMapping("/owasp/summary")
    public ResponseEntity<JsonNode> buildSummary(@RequestParam String projectKey,
                                                 @RequestParam(required = false) String branch) {
        if (projectKey == null || projectKey.isBlank()) {
            throw new IllegalArgumentException("projectKey is required.");
        }
        if (!tokenService.isSet()) {
            throw new IllegalStateException("Sonar token not set. POST /api/sonar/token first.");
        }

        String json = service.syncAndSummarize(projectKey, branch);
        try {
            return ResponseEntity.ok(mapper.readTree(json));
        } catch (Exception e) {
            throw new IllegalStateException("Service returned invalid JSON.", e);
        }
    }
}
