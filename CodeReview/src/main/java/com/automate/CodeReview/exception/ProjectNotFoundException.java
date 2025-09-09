package com.automate.CodeReview.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class ProjectNotFoundException extends ResponseStatusException {
    public ProjectNotFoundException() {
        super(HttpStatus.NOT_FOUND, "Project not found");
    }
}
