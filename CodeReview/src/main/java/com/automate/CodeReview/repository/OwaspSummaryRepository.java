package com.automate.CodeReview.repository;
import com.automate.CodeReview.entity.OwaspSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface OwaspSummaryRepository extends JpaRepository<OwaspSummary, Long> {
    Optional<OwaspSummary> findByProjectKeyAndBranch(String projectKey, String branch);
}

