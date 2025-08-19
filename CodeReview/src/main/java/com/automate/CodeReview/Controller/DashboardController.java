package com.automate.CodeReview.Controller;


import com.automate.CodeReview.Models.DashboardModel;
import com.automate.CodeReview.Models.HistoryModel;
import com.automate.CodeReview.Models.TrendsModel;
import com.automate.CodeReview.Service.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService){
        this.dashboardService = dashboardService;
    }

    @GetMapping("/{projectId}")
    public ResponseEntity<DashboardModel> getOverview(@PathVariable UUID projectId){
        return ResponseEntity.ok(dashboardService.getOverview(projectId));
    }

    @GetMapping("/{projectId}/history")
    public ResponseEntity<HistoryModel> getHistory(@PathVariable UUID projectId){
        return ResponseEntity.ok(dashboardService.getHistory(projectId));
    }

    @GetMapping("/{projectId}/trends")
    public ResponseEntity<TrendsModel> getTrends(@PathVariable UUID projectId){
        return ResponseEntity.ok(dashboardService.getTrends(projectId));
    }
}
