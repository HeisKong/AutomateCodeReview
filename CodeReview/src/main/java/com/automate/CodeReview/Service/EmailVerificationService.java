package com.automate.CodeReview.Service;

import com.automate.CodeReview.entity.UserStatus;
import com.automate.CodeReview.entity.UsersEntity;
import com.automate.CodeReview.repository.UsersRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private final UsersRepository usersRepository;
    private final VerificationTokenService verificationTokenService;
    private final EmailService emailService;
    private final ResendLimiter resendLimiter;

    @Value("${app.public-url}")
    private String appPublicUrl;

    /** ส่งอีเมลยืนยันครั้งแรก (หรือซ้ำ) */
    public void sendVerificationEmail(String email, String username) {
        String normalized = email.toLowerCase();

        if (!resendLimiter.canSend(normalized)) {
            long retryInSec = resendLimiter.retryAfterSeconds(normalized);
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Too many requests. Try again in " + retryInSec + "s."
            );
        }

        usersRepository.findByEmail(normalized).ifPresent(user -> {
            if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
                String token = verificationTokenService.generateToken(user.getEmail());
                String verifyLink = appPublicUrl + "/api/auth/verify?token=" + token;
                emailService.sendEmailVerification(user.getEmail(), user.getUsername(), verifyLink);
                resendLimiter.markSent(normalized);
            }
        });
    }

    /** ตรวจ token จากลิงก์ยืนยัน */
    public void verifyEmail(String token) {
        String email = verificationTokenService.validateAndGetEmail(token);
        UsersEntity user = usersRepository.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (user.getStatus() == UserStatus.ACTIVE) return;

        user.setStatus(UserStatus.ACTIVE);
        usersRepository.save(user);
    }
}
