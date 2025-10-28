package com.automate.CodeReview.Controller;

import com.automate.CodeReview.Models.ScanLogModel;
import com.automate.CodeReview.Models.ScanModel;
import com.automate.CodeReview.Models.ScanRequest;
import com.automate.CodeReview.Service.ScanService;
import com.automate.CodeReview.entity.ScansEntity;
import com.automate.CodeReview.repository.ScansRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@CrossOrigin(origins = "http://localhost:4200")
@RequestMapping("/api/scans")
@Slf4j
public class ScanController {

    private final ScanService scanService;
    private final ScansRepository scanRepository;

    public ScanController(ScanService scanService, ScansRepository scanRepository) {
        this.scanService = scanService;
        this.scanRepository = scanRepository;

    }

    @PostMapping("/{projectId}")
    public ResponseEntity<Map<String, Object>> scanProject(
            @PathVariable UUID projectId,
            @RequestBody ScanRequest request) {

        log.info("Received scan request for project: {}", projectId);

        Map<String, Object> result = scanService.startScan(
                projectId,
                request.getUsername(),
                request.getPassword()
        );

        return ResponseEntity.ok(result);
    }

    @GetMapping("/getProject")
    public List<ScanModel> getAllScan() {
        return scanService.getAllScan();
    }

    @GetMapping("/{scanId}")
    public ResponseEntity<ScanModel> getByIdScan(@PathVariable UUID scanId) {
        ScanModel scan = scanService.getByIdScan(scanId);
        if(scan != null){
            return ResponseEntity.ok(scan);
        }else {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{scanId}/log")
    public ResponseEntity<ScanLogModel> getScanLogById(@PathVariable UUID scanId) {
        ScanLogModel log = scanService.getScanLogById(scanId);
        return ResponseEntity.ok(log);
    }

    @DeleteMapping("/{scanId}")
    public ResponseEntity<Void> deleteScan(@PathVariable UUID scanId) {
        scanService.deleteScan(scanId);
        return ResponseEntity.noContent().build();
    }
}