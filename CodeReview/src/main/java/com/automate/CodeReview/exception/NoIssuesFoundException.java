package com.automate.CodeReview.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class NoIssuesFoundException extends ResponseStatusException {
    public NoIssuesFoundException() {
        super(HttpStatus.NOT_FOUND, "No issues found for user");
    }
}
