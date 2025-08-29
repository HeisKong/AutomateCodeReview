package com.automate.CodeReview.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;


import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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

    @Column(name = "quality_gate")
    private String qualityGate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metrics", columnDefinition = "jsonb")
    private Map<String, Object> metrics;

    @Column(name = "log_file_path")
    private String logFilePath;

    @OneToMany(mappedBy = "scan",fetch = FetchType.LAZY)
    private List<IssuesEntity> issues;

    @OneToMany(mappedBy = "scan",fetch = FetchType.LAZY)
    private List<GradeEntity> grades;
}
