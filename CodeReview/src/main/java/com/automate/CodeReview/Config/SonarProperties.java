package com.automate.CodeReview.Config;

import jakarta.validation.constraints.*;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Data
@Validated
@ConfigurationProperties("sonar")  // ผูกกับคีย์ prefix "sonar"
public class SonarProperties {

    @NotBlank
    private String hostUrl;

    private String serviceToken;

    private String webhookSecret;

    @Min(1) @Max(500)
    private int pageSize = 500;

    @Min(100) @Max(60000)
    private int connectTimeoutMs = 5000;

    @Min(500) @Max(180000)
    private int readTimeoutMs = 15000;

    @PositiveOrZero
    private int maxRetries = 2;

    @Positive
    private int batchConcurrency = 8;

    private List<String> metricsCsv = List.of(
            "bugs","vulnerabilities","code_smells","coverage","duplicated_lines_density"
    );

    private List<String> ratingMetrics = List.of(
            "security_rating","reliability_rating","maintainability_rating","sqale_rating"
    );
}