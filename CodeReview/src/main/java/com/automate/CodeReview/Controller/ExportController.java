package com.automate.CodeReview.Controller;

import com.automate.CodeReview.Service.ExportService;
import com.automate.CodeReview.dto.ReportRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/export")
public class ExportController {

    private final ExportService exportService;

    public ExportController(ExportService exportService) {
        this.exportService = exportService;
    }

    @PostMapping("/generate")
    public ResponseEntity<byte[]> generateReport(@RequestBody ReportRequest req) throws Exception{
        byte[] data = exportService.generateReport(req);
        String filename = String.format("report-%s.%s", req.projectId(), req.outputFormat());
        String mediaType = switch (req.outputFormat().toLowerCase()) {
            case "pdf" -> "application/pdf";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            default -> "application/octet-stream";
        };
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(mediaType))
                .body(data);
    }
}
