package com.automate.CodeReview.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class IssueDTO {

    private String bugs;
    private String vulnerabilities;
    private String codeSmells;
    private String coverage;
}
