package com.automate.CodeReview.repository;
import com.automate.CodeReview.entity.OwaspIssue;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface OwaspIssueRepository extends JpaRepository<OwaspIssue, Long> {
    Optional<OwaspIssue> findByIssueKey(String issueKey);
}

