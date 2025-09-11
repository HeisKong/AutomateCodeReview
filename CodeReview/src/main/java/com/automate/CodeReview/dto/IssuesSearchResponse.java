package com.automate.CodeReview.dto;
import lombok.Getter; import lombok.Setter;
import java.util.List;

@Getter @Setter
public class IssuesSearchResponse {
    private int total;
    private int p;
    private int ps;
    private List<Issue> issues;
    private List<Facet> facets;

    @Getter @Setter
    public static class Issue {
        private String key;
        private String rule;
        private String severity;     // legacy (<10.4)
        private String type;
        private String component;
        private String project;
        private String message;
        private List<String> tags;
        private Integer line;
        private String creationDate; // "2025-09-01T10:21:33+0700"
        private String updateDate;
        private String effort;
    }

    @Getter @Setter
    public static class Facet {
        private String property;     // "owaspTop10-2021" | "severities"/"impactSeverities" | "sonarsourceSecurity"
        private List<FacetValue> values;
    }

    @Getter @Setter
    public static class FacetValue {
        private String val;          // "a03" | "CRITICAL" | "sql-injection"
        private int count;
        private boolean selected;
    }
}
