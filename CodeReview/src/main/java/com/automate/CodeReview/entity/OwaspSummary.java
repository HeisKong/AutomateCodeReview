package com.automate.CodeReview.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.Getter; import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

@Entity @Table(name="owasp_summary")
@Getter @Setter
public class OwaspSummary {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    private String projectKey;
    private String branch;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition="jsonb")
    private JsonNode summaryJson;

    private Instant createdAt;
    private Instant updatedAt;
}

