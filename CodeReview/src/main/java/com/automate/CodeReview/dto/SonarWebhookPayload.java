package com.automate.CodeReview.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SonarWebhookPayload {
    private String serverUrl;
    private String taskId;
    private String status;
    private String analysedAt;
    private String changedAt;
    private Project project;
    private Branch branch;
    private QualityGate qualityGate;
    private String analysisId;
    private String revision;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Project {
        private String key;
        private String name;
        private String url;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Branch {
        private String name;
        private String type;
        // รับ isMain จาก SonarQube (true/false)
        @JsonProperty("isMain")
        private Boolean isMain;   // ชื่อฟิลด์เป็น isMain ตรงตัว จะ map อัตโนมัติ
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class QualityGate {
        private String name;
        private String status;
        // มี fields อื่น ๆ เช่น conditions ก็ปล่อยให้ ignoreUnknown ช่วยได้
    }
}
