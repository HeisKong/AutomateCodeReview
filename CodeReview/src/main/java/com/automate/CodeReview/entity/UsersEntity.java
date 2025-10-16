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
@Table(name = "users")
public class UsersEntity {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "username",  nullable = false)
    private String username;

    @Column(name = "email",   nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash",  nullable = false)
    private String password;

    @Column(name = "must_change_password", nullable = false)
    private boolean forcePasswordChange = false;

    @Column(name = "phone", nullable = false, unique = true,  length = 13)
    private String phoneNumber;

    @Column(name = "role",   nullable = false)
    private String role;

    @CreationTimestamp
    @Column(name = "created_at",  nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private List<ProjectsEntity> projects;

    @OneToMany(mappedBy = "assignedTo", fetch = FetchType.LAZY)
    private List<IssuesEntity> issues;

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private List<CommentsEntity> comments;
}
