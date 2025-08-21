package com.automate.CodeReview.repository;

import com.automate.CodeReview.entity.ProjectsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProjectsRepository extends JpaRepository<ProjectsEntity, UUID> {
}
