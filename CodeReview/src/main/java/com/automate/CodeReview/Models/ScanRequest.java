package com.automate.CodeReview.Models;

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
