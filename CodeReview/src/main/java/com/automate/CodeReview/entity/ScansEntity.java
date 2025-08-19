package com.automate.CodeReview.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

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
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @OneToMany(fetch = FetchType.LAZY)
    @JoinColumn(name = "id",   nullable = false)
    private List<ProjectsEntity> projectId;

    @Column(name = "status",  nullable = false)
    private String status;

    @CreationTimestamp
    @Column(name = "started_at",  nullable = false)
    private LocalDateTime startedAt;

    @CreationTimestamp
    @Column(name = "completed_at",  nullable = false)
    private LocalDateTime completedAt;

    @Column(name = "quality_gate",  nullable = false)
    private String qualityGate;

    @Column(name = "metrics",   nullable = false)
    private String metrics;

    @Column(name = "log_file_path",   nullable = false)
    private String logFilePath;
}
