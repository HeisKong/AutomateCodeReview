package com.automate.CodeReview.repository;

import com.automate.CodeReview.entity.NotiEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotiRepository extends JpaRepository<NotiEntity, UUID> {
    List<NotiEntity> findByReadFalse();
    List<NotiEntity> findByReadTrue();
    Optional<NotiEntity> findByNotiId(UUID notiId);
}