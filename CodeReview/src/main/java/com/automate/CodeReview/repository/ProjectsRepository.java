package com.automate.CodeReview.repository;

import com.automate.CodeReview.entity.ProjectsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
@Repository
public interface ProjectsRepository extends JpaRepository<ProjectsEntity, UUID> {
    Optional<ProjectsEntity> findBySonarProjectKey(String sonarProjectKey);
}
