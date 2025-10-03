package com.automate.CodeReview.dto;

import java.util.Map;

public record SonarSummary(
        String projectKey,
        Map<String, String> grades,
        Map<String, String> metrics
) {}