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
    List<IssuesEntity> findIssuesEntity_ByAssignedTo(UsersEntity assignedTo);
<<<<<<< Updated upstream
<<<<<<< Updated upstream

    Optional<Object> findByScan_ScanIdAndIssueKey(UUID scanId, String key);
=======
>>>>>>> Stashed changes
=======
>>>>>>> Stashed changes
}
