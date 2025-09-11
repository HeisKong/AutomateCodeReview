package com.automate.CodeReview.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * ใช้เมื่อ HMAC จาก Sonar Webhook ไม่ตรงกับ header
 */
public class InvalidWebhookSignatureException extends ResponseStatusException {
    public InvalidWebhookSignatureException() {
        super(HttpStatus.UNAUTHORIZED, "Invalid Sonar Webhook signature");
    }
}
