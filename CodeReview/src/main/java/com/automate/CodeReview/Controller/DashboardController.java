package com.automate.CodeReview.Controller;


import com.automate.CodeReview.Service.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.UUID;

@Controller
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService){
        this.dashboardService = dashboardService;
    }

    @GetMapping("/{projectId}")
    public ResponseEntity<DashboardModel> getOverview(@PathVariable UUID id){
        return ResponseEntity.ok(dashboardService.getOverview(id));
    }

    @GetMapping("/{projectId}/history")
    public ResponseEntity<HistoryModel> getHistory(@PathVariable UUID id){
        return ResponseEntity.ok(dashboardService.getHistory(id));
    }

    @GetMapping("/{projectId}/trends")
    public ResponseEntity<TrendsModel> getTrends(@PathVariable UUID id){
        return ResponseEntity.ok(dashboardService.getTrends(id));
    }
}
