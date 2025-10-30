package com.automate.CodeReview.Models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class  RepositoryModel {

    // ใช้สำหรับกรณี update หรือ mapping กลับมาจาก DB
    private UUID projectId;

    // ควรใช้เป็น list ของ userId (UUID) หรือ object ตามที่ต้องการ map
    private UUID user;

    private String name;

    private String repositoryUrl;

    private String username; // one-time

    private String password; // one-time

    private String projectType;

    private String sonarProjectKey;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}