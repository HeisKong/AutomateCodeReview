package com.automate.CodeReview.Controller;

import com.automate.CodeReview.dto.ApiMessage;
import com.automate.CodeReview.dto.ResendRequest;
import com.automate.CodeReview.Service.EmailVerificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.result.view.RedirectView;

@RestController
@RequestMapping("/api/auth/verify")
@RequiredArgsConstructor
public class EmailVerificationController {

    private final EmailVerificationService emailVerificationService;

    /**
     * ✅ ปุ่มกด Resend Email Verification
     */
    @PostMapping("/resend")
    public ResponseEntity<ApiMessage> resend(@Valid @RequestBody ResendRequest req) {
        emailVerificationService.sendVerificationEmail(req.email().toLowerCase(), null);
        return ResponseEntity.ok(new ApiMessage("If the email exists, a verification message has been sent."));
    }

    /**
     * ✅ คลิกลิงก์ยืนยันจากอีเมล
     */
    @GetMapping
    public RedirectView  verifyEmail(@RequestParam("token") String token) {
        try {
            emailVerificationService.verifyEmail(token);
            return new RedirectView("http://localhost:4200/verify-success");
        } catch (Exception e) {
            return new RedirectView("http://localhost:4200/verify-failed");
        }
    }
}
