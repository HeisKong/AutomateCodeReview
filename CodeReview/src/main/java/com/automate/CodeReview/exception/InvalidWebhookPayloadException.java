package com.automate.CodeReview.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * ใช้เมื่อ payload ที่ Sonar ส่งมา parse ไม่ได้
 */
public class InvalidWebhookPayloadException extends ResponseStatusException {
    public InvalidWebhookPayloadException(String message) {
        super(HttpStatus.BAD_REQUEST, "Invalid Sonar Webhook payload: " + message);
    }
}
