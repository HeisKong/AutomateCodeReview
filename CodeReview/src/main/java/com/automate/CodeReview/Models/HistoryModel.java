package com.automate.CodeReview.Models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class HistoryModel {

    private UUID id;
    private String name;
    private LocalDateTime createdAt;

}
