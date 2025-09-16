package com.automate.CodeReview.entity;
import jakarta.persistence.*;
import lombok.Getter; import lombok.Setter;
import java.time.Instant;

@Entity @Table(name="owasp_issue")
@Getter @Setter
public class OwaspIssue {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private String projectKey;
    private String branch;
    @Column(unique = true) private String issueKey;
    private String ruleKey;

    @Column(columnDefinition="TEXT") private String message;
    @Column(columnDefinition="TEXT") private String component;
    @Column(columnDefinition="TEXT") private String filePath;
    private Integer lineNumber;

    private String severityRaw;
    private String typeRaw;
    @Column(columnDefinition="TEXT") private String tags;

    private Instant creationDate;
    private Instant updateDate;
    private String effort;

    private String owaspCode;
    private String owaspName;
}
