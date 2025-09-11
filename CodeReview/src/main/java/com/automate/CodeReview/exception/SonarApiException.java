package com.automate.CodeReview.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * ใช้เมื่อเรียก API ของ SonarQube ล้มเหลว เช่น /api/ce/task, /api/measures/component
 */
public class SonarApiException extends ResponseStatusException {
    public SonarApiException(String message) {
        super(HttpStatus.BAD_GATEWAY, "Sonar API error: " + message);
    }
}
