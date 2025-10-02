package com.automate.CodeReview.dto;

import lombok.*;

@Data
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ScanRequest {
    private String repoUrl;
    private String projectKey;
    private String branchName;
    private String token;
}
