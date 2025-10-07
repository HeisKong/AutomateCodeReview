package com.automate.CodeReview.Models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class IssueModel {
    private UUID issueId;
    private UUID scanId;
    private UUID projectId;
    private String issueKey;
    private String type;
    private String component;
    private String message;
    private String severity;
    private String assignedTo;
    private String status;
    private String createdAt;


}
