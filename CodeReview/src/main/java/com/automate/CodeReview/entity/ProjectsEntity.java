package com.automate.CodeReview.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "projects")
public class ProjectsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID projectId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UsersEntity user;


    @Column(name = "name" , nullable = false)
    private String name;

    @Column(name = "repository_url", nullable = false, unique = true)
    private String repositoryUrl;

    @Column(name = "project_type",  nullable = false)
    private String projectType;


    @Column(name = "sonar_project_key",  nullable = false,  unique = true)
    private String sonarProjectKey;


    @CreationTimestamp
    @Column(name = "created_at",  nullable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at",   nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "project", fetch = FetchType.LAZY)
    private List<ScansEntity> scan;

    @Column(name = "clone_path" , nullable = true)
    private String clonePath;

    @Column(name = "script")
    private Boolean script;
}
