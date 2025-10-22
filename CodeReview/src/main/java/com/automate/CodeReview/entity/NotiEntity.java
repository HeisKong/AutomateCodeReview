package com.automate.CodeReview.entity;



import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "noti")
public class NotiEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "noti_id")
    private UUID notiId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id",   nullable = false)
    private ProjectsEntity project;  // FK -> projects.project_id

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scan_id", nullable = true)
    private ScansEntity scan;         // FK -> scans.scans_id

    @Column(name = "type_noti", nullable = false)
    private String typeNoti;   // scan / repo / export

    @Column(name = "message", nullable = false)
    private String message;

    @Column(nullable = false)
    private Boolean read = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;


}