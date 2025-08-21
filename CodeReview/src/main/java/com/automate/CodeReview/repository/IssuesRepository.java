package com.automate.CodeReview.repository;

import com.automate.CodeReview.entity.IssuesEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;
@Repository
public interface IssuesRepository extends JpaRepository<IssuesEntity, UUID> {
}
