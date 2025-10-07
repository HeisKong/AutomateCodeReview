package com.automate.CodeReview.Models;

import com.automate.CodeReview.dto.IssueDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class DashboardModel {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DashboardDTO {
        private UUID projectId;
        private String projectName;
        private com.fasterxml.jackson.databind.JsonNode metrics;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HistoryDTO {
        private UUID projectId;
        private String projectName;
        private String projectType;
        private String qualityGate;
        private LocalDateTime createdAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendsDTO {
        private UUID id;
        private String qualityGate;
        private String reliabilityGate;
        private String securityGate;
        private String maintainabilityGate;
        private String securityReviewGate;
        private LocalDateTime startTime;
    }


}
