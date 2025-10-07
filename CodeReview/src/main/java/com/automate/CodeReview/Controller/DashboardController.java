package com.automate.CodeReview.Controller;


import com.automate.CodeReview.Models.DashboardModel;
import com.automate.CodeReview.Service.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService){
        this.dashboardService = dashboardService;
    }

    @GetMapping("/{userId}")
    public List<DashboardModel.DashboardDTO> getOverview(@PathVariable UUID userId) {
        return dashboardService.getOverview(userId);
    }


    @GetMapping("/{userId}/history")
    public ResponseEntity<List<DashboardModel.HistoryDTO>> getHistory(@PathVariable UUID userId){
        return ResponseEntity.ok(dashboardService.getHistory(userId));
    }

    @GetMapping("/{userId}/trends")
    public ResponseEntity<List<DashboardModel.TrendsDTO>> getTrends(@PathVariable UUID userId){
        return ResponseEntity.ok(dashboardService.getTrends(userId));
    }
}
