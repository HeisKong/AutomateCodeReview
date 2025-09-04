package com.automate.CodeReview.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

public class RepositoryUrlNotFoundForProjectException extends ResponseStatusException {
    public RepositoryUrlNotFoundForProjectException(UUID projectId) {
        super(HttpStatus.NOT_FOUND, "Repository URL not found for project ID: " + projectId);
    }
}
