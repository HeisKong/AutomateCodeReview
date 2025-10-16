package com.automate.CodeReview.Service;

import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

@Service
@RequiredArgsConstructor
public class EmailService {
    private final JavaMailSender mailSender;

    public void sendResetPasswordLink(String to, String link, int ttlMinutes) {
        String subject = "Password Reset Request";
        String html = """
          <div style="font-family:Arial,sans-serif;max-width:520px">
            <h2>Password reset request</h2>
            <p>เราได้รับคำขอรีเซ็ตรหัสผ่าน</p>
            <p><a href="%s">คลิกที่นี่เพื่อรีเซ็ต</a></p>
            <p>ลิงก์นี้จะหมดอายุใน %d นาที</p>
          </div>
        """.formatted(link, ttlMinutes);

        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, "UTF-8");
            helper.setTo(to);
            // จากบัญชีเดียวกับ spring.mail.username (ปลอดภัยสุดสำหรับ Gmail)
            helper.setFrom("automatecodereviewpcc@gmail.com", "Automate CodeReview");
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(msg);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send email", e);
        }
    }
}
