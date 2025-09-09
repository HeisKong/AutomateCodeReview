package com.automate.CodeReview.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class TargetDirectoryAlreadyExistsException extends ResponseStatusException {
    public TargetDirectoryAlreadyExistsException(String path) {
        super(HttpStatus.CONFLICT, "Target directory already exists: " + path);
    }
}
