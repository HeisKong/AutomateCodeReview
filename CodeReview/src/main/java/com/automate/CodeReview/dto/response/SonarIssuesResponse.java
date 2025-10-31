package com.automate.CodeReview.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SonarIssuesResponse {
    private List<Issue> issues;
    private Paging paging;

    @Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Issue {
        private String key;
        private String type;
        private String severity;
        private String component;
        private String message;
        private String status;
        private String assigneeUuid;   // บางกรณีไม่มี
        private String assignee;       // login (fallback ถ้า uuid ไม่มี)
        private String creationDate;   // ISO-8601
    }

    @Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Paging {
        private int pageIndex;
        private int pageSize;
        private int total;
    }
}