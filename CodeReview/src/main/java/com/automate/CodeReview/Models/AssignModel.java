package com.automate.CodeReview.Models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Date;
import java.util.UUID;

@Data
public class AssignModel {

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class getAssign {
        private UUID assignedTo;
        private UUID issueId;
        private String severity;
        private String message;
        private String status;
        private LocalDate dueDate;
        private String annotation;
    }
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class setAssign{
        private UUID assignedTo;
        private UUID issueId;
        private String severity;
        private String message;
        private String status;
        private LocalDate dueDate;
        private String annotation;
    }
}
