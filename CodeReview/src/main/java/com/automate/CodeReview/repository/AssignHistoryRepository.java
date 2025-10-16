package com.automate.CodeReview.repository;

import com.automate.CodeReview.entity.AssignHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AssignHistoryRepository extends JpaRepository<AssignHistoryEntity, UUID> {

}
