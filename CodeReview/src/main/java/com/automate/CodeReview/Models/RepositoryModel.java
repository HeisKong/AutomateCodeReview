package com.automate.CodeReview.Models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RepositoryModel {

    // ใช้สำหรับกรณี update หรือ mapping กลับมาจาก DB
    private UUID id;

    // ควรใช้เป็น list ของ userId (UUID) หรือ object ตามที่ต้องการ map
    private List<UUID> userIds;

    private String name;

    private String repositoryUrl;

    private String projectType;

    private String sonarProjectKey;
}