package com.automate.CodeReview.repository;

import com.automate.CodeReview.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface    PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("DELETE FROM PasswordResetToken t WHERE t.user.userId = :userId AND t.usedAt IS NULL")
    void deleteUnusedTokensByUserId(UUID userId);

    @Modifying
    @Query("UPDATE PasswordResetToken t SET t.revoked = true " +
            "WHERE t.user.userId = :userId " +
            "AND t.usedAt IS NULL " +
            "AND t.revoked = false " +
            "AND t.tokenHash <> :currentTokenHash")
    int revokeOtherUnusedTokens(@Param("userId") UUID userId,
                                @Param("currentTokenHash") String currentTokenHash);
    @Modifying
    @Query("DELETE FROM PasswordResetToken t WHERE t.expiresAt < :cutoff")
    int deleteByExpiresAtBefore(@Param("cutoff") Instant cutoff);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM PasswordResetToken t WHERE t.tokenHash = :tokenHash")
    Optional<PasswordResetToken> findByTokenHashWithLock(@Param("tokenHash") String tokenHash);
}
