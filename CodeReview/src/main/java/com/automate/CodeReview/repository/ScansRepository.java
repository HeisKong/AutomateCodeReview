package com.automate.CodeReview.repository;

import com.automate.CodeReview.entity.ScansEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ScansRepository extends JpaRepository<ScansEntity, UUID> {
    @Query("SELECT s FROM ScansEntity s WHERE s.id = :id")
    Optional<ScansEntity> getByIdScan(@Param("id") UUID id);
    List<ScansEntity> findByProject_ProjectId(UUID projectId);
    @Query(
            value = "SELECT metrics FROM scans WHERE scans_id = :scanId",
            nativeQuery = true
    )
    String findMetricsByScanId(@Param("scanId") UUID scanId);

    Optional<ScansEntity> findFirstByProject_ProjectIdOrderByStartedAtDesc(UUID projectId);
    Optional<ScansEntity> findByAnalysisId(String analysisId);
    Optional<ScansEntity> findByDeliveryId(String deliveryId);
    Optional<ScansEntity> findTopByProject_SonarProjectKeyOrderByStartedAtDesc(String projectKey);
    Optional<ScansEntity> findTopByProject_SonarProjectKeyAndStatusInOrderByStartedAtDesc(
            String projectKey,
            List<String> statuses
    );

    List<ScansEntity> findByProject_User_UserId(UUID userId);

}
