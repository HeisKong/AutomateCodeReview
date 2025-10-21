package com.automate.CodeReview.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "scans")
public class ScansEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID scanId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id",   nullable = false)
    private ProjectsEntity project;

    @Column(name = "status",  nullable = false)
    private String status;

    @CreationTimestamp
    @Column(name = "started_at", nullable = false, updatable = false)
    private LocalDateTime startedAt;

    @UpdateTimestamp
    @Column(name = "completed_at",  nullable = true)
    private LocalDateTime completedAt;

    @Column(name="analysis_id")
    private String analysisId;

    @Column(name="delivery_id")
    private String deliveryId;

    @Column(name = "reliability_gate")
    private String reliabilityGate;

    @Column(name = "security_gate")
    private String securityGate;

    @Column(name = "maintainability_gate")
    private String maintainabilityGate;

    @Column(name = "security_review_gate")
    private String securityReviewGate;

    @Column(name = "quality_gate")
    private String qualityGate;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metrics", columnDefinition = "jsonb")
    private Map<String,Object> metrics;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", columnDefinition = "jsonb")
    private JsonNode payloadJson; // เก็บ raw payload (String/Json แล้วแต่สะดวก)

    @Column(name = "log_file_path")
    private String logFilePath;

    @OneToMany(mappedBy = "scan",fetch = FetchType.LAZY)
    private List<IssuesEntity> issues;

}
