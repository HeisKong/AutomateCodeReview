package com.automate.CodeReview.repositoty;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface Scans extends JpaRepository<Scans, UUID> {
}
