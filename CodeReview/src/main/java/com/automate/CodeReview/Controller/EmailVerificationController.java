package com.automate.CodeReview.Controller;

import com.automate.CodeReview.dto.ApiMessage;
import com.automate.CodeReview.dto.ResendRequest;
import com.automate.CodeReview.Service.EmailVerificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.result.view.RedirectView;

import java.net.URI;

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
    public ResponseEntity<Void> verifyEmail(@RequestParam("token") String token) {
        try {
            emailVerificationService.verifyEmail(token);
            return ResponseEntity
                    .status(HttpStatus.FOUND)
                    .location(URI.create("http://localhost:4200/verify-success"))
                    .build();
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.FOUND)
                    .location(URI.create("http://localhost:4200/verify-failed"))
                    .build();
        }
    }
}
