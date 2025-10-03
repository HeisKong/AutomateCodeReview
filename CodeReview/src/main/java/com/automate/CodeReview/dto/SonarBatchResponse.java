package com.automate.CodeReview.dto;

import java.util.List;
import java.util.Map;

public record SonarBatchResponse(
        List<SonarSummary> items,
        Map<String, Object> meta
) {}
