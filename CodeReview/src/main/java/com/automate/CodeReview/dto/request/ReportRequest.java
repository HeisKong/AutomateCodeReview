package com.automate.CodeReview.dto.request;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public record ReportRequest(
        UUID projectId,
        LocalDate dateFrom,
        LocalDate dateTo,
        Set<String> includeSections,
        String outputFormat
) {}
