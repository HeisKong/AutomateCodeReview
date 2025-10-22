package com.automate.CodeReview.entity;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.Date;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "assign_history")
public class AssignHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID assignId;

    @ManyToOne
    @JoinColumn(name = "issues_id", nullable = false)
    private IssuesEntity issues;

    @Column(name = "assigned_to")
    private UUID assignedTo;

    @Column(name = "status")
    private String status;

    @Column(name =  "message")
    private String message;

    @Column(name = "annotation")
    private String annotation;

    @Column(name = "due_date")
    private LocalDate dueDate;
}
