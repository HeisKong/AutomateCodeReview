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

    // Reset Password
    public void sendResetPasswordLink(String to, String link, int ttlMinutes) {
        String subject = "Reset your password";
        String html = """
      <div style="font-family: Arial, sans-serif; background:#f6f7fb; padding:24px;">
        <div style="max-width:560px; margin:0 auto; background:#ffffff; border-radius:14px; overflow:hidden; box-shadow:0 6px 20px rgba(0,0,0,0.06);">
          <div style="padding:28px 28px 8px;">
            <h2 style="margin:0 0 8px; font-size:22px; color:#111827;">Password reset request</h2>
            <p style="margin:0; color:#6b7280;">We received a request to reset your password.</p>
          </div>

          <div style="padding:20px 28px 12px;">
            <a href="%s" style="display:inline-block; text-decoration:none; padding:12px 18px; border-radius:10px; background:#3b82f6; color:#ffffff; font-weight:600;">
              Reset password
            </a>
            <p style="margin:14px 0 0; color:#374151; font-size:14px;">
              This link will expire in <strong>%d minutes</strong>.
            </p>
          </div>

          <div style="padding:0 28px 20px;">
            <p style="margin:0; color:#6b7280; font-size:13px;">
              If the button doesn’t work, copy and paste this URL into your browser:
            </p>
            <p style="word-break:break-all; margin:8px 0 0; font-size:13px;">
              <a href="%s" style="color:#2563eb; text-decoration:underline;">%s</a>
            </p>
          </div>

          <hr style="border:none; border-top:1px solid #e5e7eb; margin:0;">
          <div style="padding:16px 28px; color:#9ca3af; font-size:12px;">
            <p style="margin:0;">If you didn’t request this, you can safely ignore this email.</p>
          </div>
        </div>
      </div>
    """.formatted(link, ttlMinutes, link, link);

        sendHtml(to, subject, html);
    }

    // Registration Success
    public void sendRegistrationSuccess(String to, String username) {
        String subject = "Welcome aboard!";
        String safeName = (username != null ? username : "");
        String html = """
      <div style="font-family: Arial, sans-serif; background:#f6f7fb; padding:24px;">
        <div style="max-width:560px; margin:0 auto; background:#ffffff; border-radius:14px; overflow:hidden; box-shadow:0 6px 20px rgba(0,0,0,0.06);">
          <div style="padding:28px;">
            <h1 style="margin:0 0 6px; font-size:24px; color:#111827;">Automate Code Review 🎉</h1>
            <p style="margin:0; color:#6b7280;">Hi <strong style="color:#111827;">%s</strong>, welcome! Your account has been created successfully.</p>
          </div>

          <div style="padding:0 28px 20px;">
            <p style="margin:0 0 12px; color:#374151;">
              You can sign in anytime and start using your dashboard.
            </p>
            <a href="%s" style="display:inline-block; text-decoration:none; padding:12px 18px; border-radius:10px; background:#10b981; color:#ffffff; font-weight:600;">
              Go to Sign in
            </a>
          </div>

          <hr style="border:none; border-top:1px solid #e5e7eb; margin:0;">
          <div style="padding:16px 28px; color:#9ca3af; font-size:12px;">
            <p style="margin:0;">If you didn’t create this account, please contact support immediately.</p>
          </div>
        </div>
      </div>
    """.formatted(safeName, "http://localhost:4200/login");

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

    public void sendEmailVerification(String to, String username, String link) {
        String subject = "Verify your email address";
        String html = """
      <div style="font-family: Arial, sans-serif; background:#f6f7fb; padding:24px;">
        <div style="max-width:560px; margin:0 auto; background:#ffffff; border-radius:14px; overflow:hidden; box-shadow:0 6px 20px rgba(0,0,0,0.06);">
          <div style="padding:28px;">
            <h2 style="margin:0 0 10px; font-size:22px; color:#111827;">Verify your email</h2>
            <p style="margin:0; color:#6b7280;">Hi <strong>%s</strong>, please verify your email address to activate your account.</p>
          </div>

          <div style="padding:20px 28px;">
            <a href="%s" style="display:inline-block; text-decoration:none; padding:12px 18px; border-radius:10px; background:#10b981; color:#ffffff; font-weight:600;">
              Verify Email
            </a>
          </div>

          <hr style="border:none; border-top:1px solid #e5e7eb; margin:0;">
          <div style="padding:16px 28px; color:#9ca3af; font-size:12px;">
            <p style="margin:0;">This link will expire in 24 hours.</p>
          </div>
        </div>
      </div>
    """.formatted(username, link);

        sendHtml(to, subject, html);
    }
}