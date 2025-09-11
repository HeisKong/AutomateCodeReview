package com.automate.CodeReview.Controller;
import com.automate.CodeReview.Service.OwaspService;
import com.automate.CodeReview.Service.SonarTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sonar")
@RequiredArgsConstructor
public class OwaspController {
    private final OwaspService service;
    private final SonarTokenService tokenService;

    @PostMapping(value="/owasp/summary", produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> buildSummary(@RequestParam String projectKey,
                                               @RequestParam(required=false) String branch){
        if (!tokenService.isSet())
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Sonar token not set. POST /api/sonar/token first.");
        String json = service.syncAndSummarize(projectKey, branch);
        return ResponseEntity.ok(json);
    }
}

