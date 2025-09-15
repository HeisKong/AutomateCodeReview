package com.automate.CodeReview.Models;



import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TrendsModel {

    private UUID id;
    private String qualityGate;
    private String reliabilityGate;
    private String securityGate;
    private String maintainabilityGate;
    private String securityReviewGate;

    private LocalDateTime startTime;

}
