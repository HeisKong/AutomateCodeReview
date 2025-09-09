package com.automate.CodeReview.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class ProjectsNotFoundForUserException extends ResponseStatusException {
    public ProjectsNotFoundForUserException() {
        super(HttpStatus.NOT_FOUND, "Projects not found for user");
    }
}
