package com.automate.CodeReview.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.util.Date;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "gate_history")
public class GradeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID gateId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name =  "scan_id")
    private ScansEntity scan;

    @Column(name = "quality_gate")
    private String qualityGate;

    @CreationTimestamp
    @Column(name = "created_at")
    private Date createdAt;
}
