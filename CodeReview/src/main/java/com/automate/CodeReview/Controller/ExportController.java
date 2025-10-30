package com.automate.CodeReview.Controller;

import com.automate.CodeReview.Service.ExportService;
import com.automate.CodeReview.dto.request.ReportRequest;
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
        String format = req.outputFormat().toLowerCase();

        // ตรวจสอบว่าควรเป็น ZIP หรือไม่
        boolean isZip = !"xlsx".equals(format) && req.includeSections() != null && req.includeSections().size() > 1;

        String filename;
        String mediaType;
        String dateStr = new java.text.SimpleDateFormat("yyMMdd").format(new java.util.Date());

        if (isZip) {
            // กรณีเป็น ZIP
            filename = String.format("report_%s.zip",dateStr);
            mediaType = "application/zip";
        } else {
            // กรณีไฟล์เดียว (xlsx หรือ section เดียว)
            String projectName = exportService.getProjectNameForExport(req.projectId());
            String sectionName = String.join(", ", req.includeSections());
            filename = String.format("%s_%s_%s.%s", sectionName, projectName, dateStr, format);
            if ("xlsx".equals(format)) {
                filename = String.format("Report_%s_%s.%s", projectName, dateStr, format);
            }

            mediaType = switch (format) {
                case "pdf" -> "application/pdf";
                case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
                default -> "application/octet-stream";
            };
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(mediaType))
                .contentLength(data.length)
                .body(data);
    }
}
