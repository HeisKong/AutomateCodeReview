package com.automate.CodeReview.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
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
        private java.util.List<Condition> conditions;
        // มี fields อื่น ๆ เช่น conditions ก็ปล่อยให้ ignoreUnknown ช่วยได้
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Condition {
        // metric key
        @JsonAlias({"metricKey","metric"})
        private String metricKey;

        // comparator/operator
        @JsonAlias({"comparator","operator","op"})
        private String comparator;

        // threshold & actual
        @JsonAlias({"errorThreshold","error"})
        private String errorThreshold;

        @JsonAlias({"actualValue","actual"})
        private String actualValue;

        // บาง payload มี status บางตัวไม่มี
        private String status;

        private String value;
    }
}
