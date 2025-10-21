package com.automate.CodeReview.Models;


import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class NotiModel {
    private Long notiId;
    private UUID projectId;
    private UUID scanId;
    private String typeNoti;
    private String message;
    private Boolean read;
    private LocalDateTime createdAt;
}
