package com.automate.CodeReview.dto.response;

import com.automate.CodeReview.dto.SonarSummary;

import java.util.List;
import java.util.Map;

public record SonarBatchResponse(
        List<SonarSummary> items,
        Map<String, Object> meta
) {}
