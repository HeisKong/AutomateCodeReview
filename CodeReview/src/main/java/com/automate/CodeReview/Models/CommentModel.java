package com.automate.CodeReview.Models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CommentModel {

    private UUID issueId;
    private UUID userId;
    private String comment;
    private LocalDateTime createdAt;
}
