package com.automate.CodeReview.dto;

import java.util.Map;

public record SonarSummary(
        String projectKey,
        Map<String, String> grades,  // {security, reliability, maintainability}
        Map<String, String> metrics  // {coverage, duplication, bugs, vulnerabilities, codeSmells}
) {}