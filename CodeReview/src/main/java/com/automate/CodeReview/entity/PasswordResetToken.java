package com.automate.CodeReview.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "password_reset_tokens",
        indexes = {
                @Index(name = "idx_prt_token_hash", columnList = "token_hash", unique = true)
        }
)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class PasswordResetToken {

    // ให้แอป generate UUID เอง (สอดคล้องกับ DDL ที่ตั้ง DEFAULT ได้ แต่ไม่จำเป็นต้องพึ่ง DB)
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** FK -> users.user_id */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",                 // คอลัมน์ในตารางนี้
            referencedColumnName = "user_id", // คอลัมน์เป้าหมายในตาราง users
            nullable = false
    )
    private UsersEntity user;

    /** ตรงกับ token_hash VARCHAR(128) + unique index */
    @Column(name = "token_hash", nullable = false, length = 128, unique = true)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "revoked", nullable = false)
    private boolean revoked;

    @Transient
    public boolean isUsable() {
        return usedAt == null && !revoked && Instant.now().isBefore(expiresAt);
    }
}
