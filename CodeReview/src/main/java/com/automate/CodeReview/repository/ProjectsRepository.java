package com.automate.CodeReview.repository;

import com.automate.CodeReview.entity.ProjectsEntity;
import com.automate.CodeReview.entity.UsersEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectsRepository extends JpaRepository<ProjectsEntity, UUID> {
    List<ProjectsEntity> findByUser_UserId(UUID userId);

    Optional<ProjectsEntity> findBySonarProjectKey(String sonarProjectKey);

    Optional<String> findRepositoryUrlByProjectId(UUID projectId);

    String user(UsersEntity user);

}
