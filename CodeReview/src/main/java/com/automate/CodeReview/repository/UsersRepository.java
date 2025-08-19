package com.automate.CodeReview.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UsersRepository extends JpaRepository<UsersRepository, UUID> {
}
