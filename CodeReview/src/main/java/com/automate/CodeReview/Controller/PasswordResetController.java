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
    /**
     * Endpoint: POST /auth/password-reset/request
     * Body: { "email": "user@example.com" }
     */
//    @PostMapping("/request")
//    public ResponseEntity<Map<String, Object>> requestReset(@RequestBody Map<String, String> body) {
//        String email = body.get("email");
//        if (email == null || email.isBlank()) {
//            return ResponseEntity.badRequest().body(Map.of(
//                    "status", "FAILED",
//                    "message", "Email is required"
//            ));
//        }
//
//        // เสมอ: ตอบ SUCCESS เพื่อป้องกันการเดาอีเมล (ไม่บอกว่าเจอหรือไม่เจอ)
//        passwordResetService.requestReset(email.trim());
//
//        return ResponseEntity.ok(Map.of(
//                "status", "SUCCESS",
//                "message", "If the account exists, a reset link has been sent."
//        ));
//    }

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

        // ✅ โปรดักชัน / โปรไฟล์อื่น: ไม่เปิดเผย token
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "If the account exists, a reset link has been sent."
        ));
    }

    /**
     * Endpoint: POST /auth/password-reset/confirm
     * Body: { "token": "...", "newPassword": "MyNewPass123" }
     */
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
