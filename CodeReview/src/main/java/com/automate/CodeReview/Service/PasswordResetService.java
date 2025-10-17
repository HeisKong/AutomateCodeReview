package com.automate.CodeReview.Service;

import com.automate.CodeReview.entity.PasswordResetToken;
import com.automate.CodeReview.entity.UsersEntity;
import com.automate.CodeReview.repository.PasswordResetTokenRepository;
import com.automate.CodeReview.repository.UsersRepository;
import com.automate.CodeReview.util.TokenHashUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
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
import org.springframework.core.env.Environment;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final UsersRepository usersRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService; // คุณต้องมี service ส่งเมลเอง
    private final Environment env;

    @Value("${app.frontend.reset-password-url}")
    private String resetPasswordPage;

    @Value("${app.security.reset-token-ttl-minutes:30}")
    private long tokenTtlMinutes;

    @Value("${app.debug.expose-reset-token:false}")
    private boolean exposeResetToken;
    /**
     * สร้าง reset token + ส่งลิงก์ทางอีเมล
     */
//    @Transactional
//    public void requestReset(String email) {
//        var userOpt = usersRepository.findByEmail(email);
//        if (userOpt.isEmpty()) {
//            // ป้องกัน user enumeration: ทำเงียบๆ แล้วจบ
//            return;
//        }
//        var user = userOpt.get();
//
//        // ยกเลิก token เดิมที่ยังไม่ใช้
//        tokenRepository.findAll().stream()
//                .filter(t -> t.getUser().getUserId().equals(user.getUserId()) && t.getUsedAt() == null && !t.isRevoked())
//                .forEach(t -> { t.setRevoked(true); tokenRepository.save(t); });
//
//        // สร้าง raw token + เก็บ hash
//        String rawToken = java.util.UUID.randomUUID().toString();
//        String tokenHash = TokenHashUtils.sha256(rawToken);
//
//        var token = PasswordResetToken.builder()
//                .user(user)
//                .tokenHash(tokenHash)
//                .expiresAt(java.time.Instant.now().plus(java.time.Duration.ofMinutes(tokenTtlMinutes)))
//                .revoked(false)
//                .build();
//        tokenRepository.save(token);
//
//        // ประกอบลิงก์ไปหน้า Frontend (encode ไว้เผื่อ)
//        String encoded = java.net.URLEncoder.encode(rawToken, java.nio.charset.StandardCharsets.UTF_8);
//        String link = resetPasswordPage + "?token=" + encoded;
//
//        // ส่งอีเมล (ล้มเหลวก็ไม่บอก client)
//        try {
//            emailService.sendResetPasswordLink(user.getEmail(), link, (int) tokenTtlMinutes);
//        } catch (Exception ex) {
//            // log เตือนพอ ไม่ throw ออกไป กันเปิดเผยว่ามีอีเมลจริง
//            // log.warn("Failed to send reset email to {}", user.getEmail(), ex);
//        }
//
//    }

    @Transactional
    public Optional<String> requestReset(String email) {
        var userOpt = usersRepository.findByEmail(email);
        if (userOpt.isEmpty()) return Optional.empty();

        var user = userOpt.get();

        // 💡 ลบเฉพาะ token เก่าของ user ที่ยัง unused
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
        } catch (Exception ignore) {}

        boolean isDevOrLocal = env.acceptsProfiles(Profiles.of("dev", "local"));
        boolean shouldExpose = isDevOrLocal || exposeResetToken;

        return shouldExpose ? Optional.of(rawToken) : Optional.empty();
    }


    @Transactional
    public void confirmReset(String rawToken, String newPassword) {
        if (rawToken == null || rawToken.isBlank() || newPassword == null || newPassword.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired token");
        }

        // ป้องกันเคสบาง client เปลี่ยน '+' เป็น space โดยไม่ตั้งใจ
        String normalized = rawToken.trim();

        // (ทางเลือก) ตรวจ policy รหัสผ่าน
        // if (!PasswordPolicy.isValid(newPassword)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Weak password");

        String tokenHash = TokenHashUtils.sha256(normalized);

        // ล็อกแถวกัน concurrent confirm
        PasswordResetToken token = tokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired token"));

        // ตรวจสถานะใช้งานได้ + หมดอายุ
        if (token.isRevoked()
                || token.getUsedAt() != null
                || java.time.Instant.now().isAfter(token.getExpiresAt())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired token");
        }

        // เปลี่ยนรหัสผ่าน
        UsersEntity user = token.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setForcePasswordChange(false);
        usersRepository.save(user);

        // mark token นี้ว่าใช้แล้ว
        token.setUsedAt(java.time.Instant.now());
        token.setRevoked(true);
        tokenRepository.save(token);

        // revoke token reset อื่นๆ ของ user นี้ที่ยังไม่ใช้
        tokenRepository.findAll().stream()
                .filter(t -> t.getUser().getUserId().equals(user.getUserId())
                        && t.getUsedAt() == null
                        && !t.isRevoked()
                        && !t.getTokenHash().equals(tokenHash))
                .forEach(t -> { t.setRevoked(true); tokenRepository.save(t); });

        // (ทางเลือก) Invalidate sessions/refresh tokens ของ user นี้ทั้งหมด
        // refreshTokenService.revokeAllForUser(user.getId());
    }

}
