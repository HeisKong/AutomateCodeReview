package com.automate.CodeReview.Controller;


import com.automate.CodeReview.Service.ScanService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.UUID;

@Controller
@RequestMapping("/api/scans")
public class ScanController {

    private final ScanService scanService;

    public ScanController(ScanService scanService) {
        this.scanService = scanService;
    }

    @PostMapping
    public ResponseEntity<ScanModel> startScan(@PathVariable UUID repositoryId) {
        ScanModel scan = scanService.startScan(repositoryId);
        return ResponseEntity.ok(scan);
    }

    @GetMapping
    public List<ScanModel> getAllScan() {
        return scanService.getAllScan();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ScanModel> getByIdScan(@PathVariable UUID id) {
        ScanModel scan = scanService.getByIdScan(id);
        if(scan != null){
            return ResponseEntity.ok(scan);
        }else {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{id}/log")
    public ResponseEntity<ScanLogModel> getLogScan(@PathVariable UUID id) {
        return ResponseEntity.ok(scanService.getLogScan(id));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ScanModel> cancelScan(@PathVariable UUID id) {
        return ResponseEntity.ok(scanService.cancelScan(id));
    }
}
