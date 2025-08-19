package com.automate.CodeReview.repositoty;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface IssuesRepository extends JpaRepository<IssuesRepository, UUID> {
}
