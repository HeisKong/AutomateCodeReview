package com.automate.CodeReview.repository;

import com.automate.CodeReview.entity.CommentsEntity;
import com.automate.CodeReview.entity.IssuesEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
@Repository
public interface CommentsRepository extends JpaRepository<CommentsEntity, UUID> {
    List<CommentsEntity> findByIssues(IssuesEntity issue);

}
