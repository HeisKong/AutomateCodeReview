package com.automate.CodeReview.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LogPayload {
    private String projectKey;
    private String scanId;
    private String logFilePath;
    private String source;
    private LocalDateTime timestamp = LocalDateTime.now();
}
