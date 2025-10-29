package com.automate.CodeReview.repository;

import com.automate.CodeReview.entity.IssuesEntity;
import com.automate.CodeReview.entity.UsersEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IssuesRepository extends JpaRepository<IssuesEntity, UUID> {
    List<IssuesEntity> findByScan_Project_User_UserId(UUID userId);
    List<IssuesEntity> findByScan_Project_ProjectId(UUID projectId);
    Optional<IssuesEntity> findByScan_ScanIdAndIssueKey(UUID scanId, String issueKey);
    List<IssuesEntity> findByAssignedTo_UserId(UUID userId);
    List<IssuesEntity> findByScan_Project_ProjectIdAndAssignedTo_UserId(UUID projectId, UUID userId);
}
