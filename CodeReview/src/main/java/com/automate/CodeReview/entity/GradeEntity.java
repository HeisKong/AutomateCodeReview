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

    @Column(name = "reliability_gate")
    private String reliabilityGate;

    @Column(name = "security_gate")
    private String securityGate;

    @Column(name = "maintainability_gate")
    private String maintainabilityGate;

    @Column(name = "security_review_gate")
    private String securityReviewGate;
    @CreationTimestamp
    @Column(name = "created_at")
    private Date createdAt;
}
