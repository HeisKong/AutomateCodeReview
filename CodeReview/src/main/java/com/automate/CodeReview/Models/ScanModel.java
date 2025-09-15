package com.automate.CodeReview.Models;

import lombok.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ScanModel {

    private String sonarProjectKey;
    private String repositoryUrl;
    private int exitCode;
    private UUID scanId;
    private UUID projectId;
    private String branchName;
    private String status;
    private String qualityGate;
    private String reliabilityGate;
    private String securityGate;
    private String maintainabilityGate;
    private String securityReviewGate;
    private Map<String, Object> metrics;           // เก็บเป็น JSON string ตาม entity
    private String logFilePath;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String output;
}
