package com.automate.CodeReview.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class IssueNotFoundException extends ResponseStatusException {
    public IssueNotFoundException() {
        super(HttpStatus.NOT_FOUND, "Issue not found");
    }
}

