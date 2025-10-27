package com.automate.CodeReview.Models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
public class DashboardModel {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DashboardDTO {
        private UUID projectId;
        private String projectName;
        private Map<String, Object>metrics;
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
