package com.automate.CodeReview.Models;

import com.automate.CodeReview.dto.IssueDTO;
import lombok.*;

import java.util.UUID;

@Data
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class DashboardModel {

    private UUID projectId;
    private String projectName;
    private IssueDTO metrics;

    public void setMetrics(String number) {
    }
}
