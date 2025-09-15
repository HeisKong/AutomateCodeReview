package com.automate.CodeReview.Config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties("sonar")  // ผูกกับคีย์ prefix "sonar"
public class SonarProperties {
    @NotBlank private String hostUrl;            // http(s)://sonar:9000
    private String serviceToken;                 // เว้นได้ถ้า dev เปิด anonymous
    private String webhookSecret;                // ถ้าตั้ง secret ที่หน้า Webhook
    @Min(50) @Max(5000)
    private int pageSize = 500;
    @Min(1000) @Max(60000)
    private int connectTimeoutMs = 5000;
    @Min(1000) @Max(120000)
    private int readTimeoutMs = 15000;
    private String metricsCsv;
}