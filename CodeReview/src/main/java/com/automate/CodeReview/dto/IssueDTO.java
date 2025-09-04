package com.automate.CodeReview.dto;

import lombok.*;

@Data
public class IssueDTO {

    private int bugs;
    private int vulnerabilities;
    private int codeSmells;
    private int coverage;
}
