package com.automate.CodeReview.repository;

import com.automate.CodeReview.entity.GradeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
@Repository
public interface GradeRepository extends JpaRepository<GradeEntity, UUID> {
    List<GradeEntity> findByScan_ScanId(UUID scanId);
}
