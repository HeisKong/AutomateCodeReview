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

    //  Reset Password
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

        sendHtml(to, subject, html);
    }

    //  register
    public void sendRegistrationSuccess(String to, String username) {
        String subject = "ยืนยันการสมัครสมาชิก";
        String html = """
          <div style="font-family:Arial,sans-serif;max-width:520px">
            <h2>สมัครสมาชิกสำเร็จ 🎉</h2>
            <p>สวัสดีคุณ <b>%s</b>,</p>
            <p>บัญชีของคุณถูกสร้างเรียบร้อยแล้วในระบบของเรา</p>
            <p>หากคุณไม่ได้เป็นคนสมัคร กรุณาติดต่อทีมงานโดยด่วน</p>
            <hr/>
            <small>ขอบคุณที่ใช้บริการ</small>
          </div>
        """.formatted(username != null ? username : "");

        sendHtml(to, subject, html);
    }

    //  utility
    private void sendHtml(String to, String subject, String html) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, "UTF-8");
            helper.setTo(to);
            helper.setFrom("automatecodereviewpcc@gmail.com", "Automate CodeReview");
            helper.setSubject(subject);
            helper.setText(html, true); // true = HTML
            mailSender.send(msg);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send email", e);
        }
    }
}