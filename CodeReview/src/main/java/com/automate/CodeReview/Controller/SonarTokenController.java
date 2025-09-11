package com.automate.CodeReview.Controller;
import com.automate.CodeReview.Service.SonarTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/sonar/token")
@RequiredArgsConstructor
public class SonarTokenController {
    private final SonarTokenService tokenService;

    @PostMapping
    public ResponseEntity<String> setToken(@RequestBody Map<String,String> body){
        String token = body.get("token");
        if (token == null || token.isBlank()) return ResponseEntity.badRequest().body("token is required");
        tokenService.updateToken(token);
        return ResponseEntity.ok("token updated");
    }
}
