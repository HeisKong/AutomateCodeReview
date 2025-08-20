package com.automate.CodeReview.repository;

import com.automate.CodeReview.entity.ScansEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
@Repository
public interface ScansRepository extends JpaRepository<ScansRepository, UUID> {
    @Query("SELECT s FROM ScansEntity s WHERE s.id = :id")
    Optional<ScansEntity> getByIdScan(@Param("id") UUID id);


}
