package com.automate.CodeReview.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {
    private final JavaMailSender mailSender;
    private final Logger log = LoggerFactory.getLogger(EmailService.class);

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendResetPassword(String toEmail, String tempPassword) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("Password Reset Request");
        message.setText("Your temporary password is: " + tempPassword + "\nPlease change after login.");
        try {
            mailSender.send(message);
            log.info("Reset password email sent to {}", toEmail);
        } catch (MailException e) {
            log.error("Failed to send reset email to {}: {}", toEmail, e.getMessage(), e);
            throw e; // let caller decide how to handle
        }
    }
}


