package com.automate.CodeReview.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 500)
    private String token;

    @Column(nullable = false)
    private Instant expiryDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UsersEntity user;  // ต้องมี entity Users ที่ @Entity(name = "users")

    public RefreshToken() {}

    public RefreshToken(String token, Instant expiryDate, UsersEntity user) {
        this.token = token;
        this.expiryDate = expiryDate;
        this.user = user;
    }

}
