package com.automate.CodeReview.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface IssuesRepository extends JpaRepository<IssuesRepository, UUID> {
}
