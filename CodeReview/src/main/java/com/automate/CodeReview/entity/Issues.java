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
@Table(name = "issues")
public class Issues {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private UUID id;

    @OneToMany(fetch = FetchType.LAZY)
    @Column(name = "id",  nullable = false, unique = true)
    private List<Scans> scanId;

    @Column(name = "issue_key",  nullable = false)
    private String issueKey;

    @Column(name = "type",  nullable = false)
    private String type;

    @Column(name = "severity",   nullable = false)
    private String severity;

    @Column(name = "component",  nullable = false)
    private String component;

    @Column(name = "message",  nullable = false)
    private String message;

    @OneToMany(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private List<Projects> assignedTo;

    @Column(name = "status",  nullable = false)
    private String status;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
