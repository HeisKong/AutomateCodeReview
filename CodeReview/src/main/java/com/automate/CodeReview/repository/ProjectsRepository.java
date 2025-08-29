package com.automate.CodeReview.repository;

import com.automate.CodeReview.entity.ProjectsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
@Repository
public interface ProjectsRepository extends JpaRepository<ProjectsEntity, UUID> {
    @Query("SELECT p.repositoryUrl FROM ProjectsEntity p WHERE p.projectId = :projectId")
    Optional<String> findRepositoryUrlByProjectId(@Param("projectId") UUID projectId);

    Optional<ProjectsEntity> findBySonarProjectKey(String sonarProjectKey);
}
