package com.automate.CodeReview.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record ReportRequest(
        UUID projectId,
        String reportType,
        LocalDate dateFrom,
        LocalDate dateTo,
        Set<String> includeSections,               // เช่น ["QualityGateSummary", ...]
        Map<String, List<String>> selectedColumns, // key = sectionName, value = list ของ column ที่เลือก
        String outputFormat
) {}
