package com.automate.CodeReview.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AssignRequest {
    private UUID assignTo;
    @NotNull
    private LocalDate dueDate;
}
