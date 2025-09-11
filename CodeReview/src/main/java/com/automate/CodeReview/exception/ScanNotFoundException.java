package com.automate.CodeReview.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

// เมื่อหา Scan ไม่เจอ
public class ScanNotFoundException extends ResponseStatusException {
    public ScanNotFoundException(UUID id) {
        super(HttpStatus.NOT_FOUND, "Scan not found with id: " + id);
    }
}

