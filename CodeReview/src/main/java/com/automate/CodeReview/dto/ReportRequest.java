package com.automate.CodeReview.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record ReportRequest(
        UUID projectId,
        LocalDate dateFrom,
        LocalDate dateTo,
        Set<String> includeSections,
        String outputFormat
) {}
