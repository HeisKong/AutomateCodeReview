package com.automate.CodeReview.entity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "issues")
public class IssuesEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID issuesId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scan_id", nullable = false)
    private ScansEntity scan;

    @Column(name = "issue_key")
    private String issueKey;

    @Column(name = "type",  nullable = false)
    private String type;

    @Column(name = "severity",   nullable = false)
    private String severity;

    @Column(name = "component",  nullable = false)
    private String component;

    @Column(name = "message",  nullable = false)
    private String message;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to")
    private UsersEntity assignedTo;

    @Column(name = "status",  nullable = false)
    private String status;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "issues" ,fetch = FetchType.LAZY)
    private List<CommentsEntity> comments;

    @OneToMany(mappedBy = "issues", fetch = FetchType.LAZY)
    private List<AssignHistoryEntity> assignHistory;
}
