package com.automate.CodeReview.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class GitCloneException extends ResponseStatusException {
    public GitCloneException(String message) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, "Error cloning repository: " + message);
    }
}
