package com.automate.CodeReview.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record RepositoryResponse(
        UUID projectId,
        UUID userId,
        String name,
        String repositoryUrl,
        String projectType,
        String sonarProjectKey,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String clonePath
) {}