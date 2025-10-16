package com.automate.CodeReview.repository;

import com.automate.CodeReview.entity.RefreshToken;
import com.automate.CodeReview.entity.UsersEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByToken(String token);
    long deleteByUser(UsersEntity user);
    void deleteByToken(String token);
    long deleteByExpiryDateBefore(Instant now);
}