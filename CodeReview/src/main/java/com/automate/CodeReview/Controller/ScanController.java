package com.automate.CodeReview.Controller;


import com.automate.CodeReview.Models.ScanLogModel;
import com.automate.CodeReview.Models.ScanModel;
import com.automate.CodeReview.Service.ScanService;
import com.automate.CodeReview.entity.ScansEntity;
import com.automate.CodeReview.repository.ScansRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/scans")
public class ScanController {

    private final ScanService scanService;
    private final ScansRepository scanRepository;

    public ScanController(ScanService scanService, ScansRepository scanRepository) {
        this.scanService = scanService;
        this.scanRepository = scanRepository;

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
    public ResponseEntity<Optional<ScansEntity>> getByIdScan(@PathVariable UUID id) {
        Optional<ScansEntity> scan = scanRepository.getByIdScan(id);
        if(scan != null){
            return ResponseEntity.ok(scan);
        }else {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{id}/log")
    public ResponseEntity<ScanLogModel> getLogScan(@PathVariable UUID id) {
        ScanLogModel log = scanService.getScanLogById(id);
        return ResponseEntity.ok(log);
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ScanModel> cancelScan(@PathVariable UUID id) {
        return ResponseEntity.ok(scanService.cancelScan(id));
    }
}