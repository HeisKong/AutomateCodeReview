package com.automate.CodeReview.Controller;

import com.automate.CodeReview.Service.PasswordResetService;
import com.automate.CodeReview.dto.response.PasswordResetConfirm;
import com.automate.CodeReview.dto.request.PasswordResetRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Arrays;
import java.util.Map;

@RestController
@CrossOrigin(origins = "http://localhost:4200")
@RequestMapping("/api/auth/password-reset")
@RequiredArgsConstructor
public class PasswordResetController {

    private final PasswordResetService passwordResetService;
    private final Environment env;

    @PostMapping("/request")
    public ResponseEntity<Map<String, Object>> requestReset(@RequestBody PasswordResetRequest req) {
        if (req == null || req.email() == null || req.email().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "FAILED", "message", "Email is required"
            ));
        }

        var devToken = passwordResetService.requestReset(req.email().trim());
        boolean isDev = Arrays.asList(env.getActiveProfiles()).contains("dev");

        if (isDev && devToken.isPresent()) {
            // ✅ เฉพาะ dev เท่านั้นที่แนบ token ออกมาให้ลอง
            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "message", "If the account exists, a reset link has been sent.",
                    "devToken", devToken.get()
            ));
        }

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "If the account exists, a reset link has been sent."
        ));
    }


    @PostMapping("/confirm")
    public ResponseEntity<Map<String, Object>> confirmReset(@RequestBody PasswordResetConfirm req) {
        String token = req.token();
        String newPassword = req.newPassword();

        if (token == null || token.isBlank() || newPassword == null || newPassword.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "FAILED",
                    "message", "Token and newPassword are required"
            ));
        }
        passwordResetService.confirmReset(token.trim(), newPassword.trim());
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Password has been reset"
        ));
    }
}
