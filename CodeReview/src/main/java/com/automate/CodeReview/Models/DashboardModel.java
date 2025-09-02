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

    private UUID id;
    private String name;
    private IssueDTO metrics;

    public void setMetrics(String number) {
    }
}
