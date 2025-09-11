package com.automate.CodeReview.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

// เมื่อ Cancel ไม่ได้เพราะสถานะไม่ใช่ RUNNING
public class ScanCancelConflictException extends ResponseStatusException {
    public ScanCancelConflictException(UUID id, String status) {
        super(HttpStatus.CONFLICT, "Cannot cancel scan " + id + " because status = " + status);
    }
}
