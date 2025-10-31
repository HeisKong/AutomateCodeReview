package com.automate.CodeReview.Service;

import com.automate.CodeReview.entity.PasswordResetToken;
import com.automate.CodeReview.entity.UsersEntity;
import com.automate.CodeReview.repository.PasswordResetTokenRepository;
import com.automate.CodeReview.repository.UsersRepository;
import com.automate.CodeReview.util.TokenHashUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final UsersRepository usersRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final Environment env;

    @Value("${app.frontend.reset-password-url}")
    private String resetPasswordPage;

    @Value("${app.security.reset-token-ttl-minutes:30}")
    private long tokenTtlMinutes;

    @Value("${app.debug.expose-reset-token:false}")
    private boolean exposeResetToken;

    @Value("${app.security.password-min-length:6}")
    private int passwordMinLength;

    /**
     * ขอ reset password - สร้าง token และส่งอีเมล
     * @return Optional ของ raw token (เฉพาะ dev/local environment)
     */
    @Transactional
    public Optional<String> requestReset(String email) {
        if (email == null || email.isBlank()) {
            log.warn("Password reset requested with null/empty email");
            return Optional.empty();
        }

        var userOpt = usersRepository.findByEmail(email.trim().toLowerCase());
        if (userOpt.isEmpty()) {
            log.info("Password reset requested for non-existent email: {}", email);
            return Optional.empty();
        }

        var user = userOpt.get();

        // delete old token
        tokenRepository.deleteUnusedTokensByUserId(user.getUserId());

        String rawToken = UUID.randomUUID().toString();
        String tokenHash = TokenHashUtils.sha256(rawToken);

        var token = PasswordResetToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plus(Duration.ofMinutes(tokenTtlMinutes)))
                .revoked(false)
                .build();

        tokenRepository.save(token);

        String encoded = URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
        String link = resetPasswordPage + "?token=" + encoded;

        try {
            emailService.sendResetPasswordLink(user.getEmail(), link, (int) tokenTtlMinutes);
            log.info("Password reset email sent to: {}", user.getEmail());
        } catch (Exception e) {
            log.error("Failed to send password reset email to {}: {}",
                    user.getEmail(), e.getMessage(), e);
        }

        boolean isDevOrLocal = env.acceptsProfiles(Profiles.of("dev", "local"));
        boolean shouldExpose = isDevOrLocal || exposeResetToken;

        if (shouldExpose) {
            log.debug("Exposing reset token for development: {}", rawToken);
        }

        return shouldExpose ? Optional.of(rawToken) : Optional.empty();
    }

    /**
     * ยืนยันการ reset password - ตรวจสอบ token และเปลี่ยนรหัสผ่าน
     */
    @Transactional
    public void confirmReset(String rawToken, String newPassword) {
        // Validation
        if (rawToken == null || rawToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired token");
        }
        if (newPassword == null || newPassword.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password is required");
        }

        String normalized = rawToken.trim();

        if (normalized.length() > 256) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid token format");
        }

        if (newPassword.length() < passwordMinLength) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    String.format("Password must be at least %d characters", passwordMinLength));
        }

        String tokenHash = TokenHashUtils.sha256(normalized);

        PasswordResetToken token = tokenRepository.findByTokenHashWithLock(tokenHash)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Invalid or expired token"));

        if (token.isRevoked() || token.getUsedAt() != null) {
            log.warn("Attempted to use revoked/used token: {}", tokenHash.substring(0, 8));
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired token");
        }

        if (Instant.now().isAfter(token.getExpiresAt())) {
            log.warn("Attempted to use expired token: {}", tokenHash.substring(0, 8));
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired token");
        }

        UsersEntity user = token.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setForcePasswordChange(false);
        usersRepository.save(user);

        token.setUsedAt(Instant.now());
        token.setRevoked(true);
        tokenRepository.save(token);

        int revoked = tokenRepository.revokeOtherUnusedTokens(user.getUserId(), tokenHash);

        log.info("Password reset successful for user: {} (revoked {} other tokens)",
                user.getEmail(), revoked);
    }

    /**
     * ลบ token ที่หมดอายุแล้ว (ควรรัน daily via scheduler)
     */
    @Scheduled(cron = "${app.security.cleanup-cron:0 0 2 * * *}")
    @Transactional
    public void cleanupExpiredTokens() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(7));
        int deleted = tokenRepository.deleteByExpiresAtBefore(cutoff);
        log.info("Cleaned up {} expired password reset tokens", deleted);
    }
}