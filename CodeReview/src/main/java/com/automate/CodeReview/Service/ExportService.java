package com.automate.CodeReview.Service;


import com.automate.CodeReview.dto.request.ReportRequest;
import lombok.extern.slf4j.Slf4j;
import net.sf.jasperreports.engine.*;

import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import net.sf.jasperreports.engine.export.JRPdfExporter;
import net.sf.jasperreports.engine.export.ooxml.JRDocxExporter;
import net.sf.jasperreports.engine.export.ooxml.JRPptxExporter;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;

import org.springframework.jdbc.core.JdbcTemplate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.*;


@Service
@Slf4j
public class ExportService {

    private final JdbcTemplate jdbcTemplate;
    private final NotiService notiService;

    public ExportService(JdbcTemplate jdbcTemplate, NotiService notiService) {
        this.jdbcTemplate = jdbcTemplate;
        this.notiService = notiService;
    }

    public byte[] generateReport(ReportRequest req) {
        ZoneId Z = ZoneId.of("Asia/Bangkok");
        Timestamp fromTs = Timestamp.from(req.dateFrom().atStartOfDay(Z).toInstant());
        Timestamp toExcl = Timestamp.from(req.dateTo().plusDays(1).atStartOfDay(Z).toInstant());

        Date dateFromInclDisplay = Date.from(req.dateFrom().atStartOfDay(Z).toInstant());
        Date dateToInclDisplay = Date.from(req.dateTo().atStartOfDay(Z).toInstant());

        String fmt = (req.outputFormat() == null ? "pdf" : req.outputFormat().toLowerCase());

        // สำหรับ Excel ให้ใช้วิธีพิเศษ
        if ("xlsx".equals(fmt)) {
            try {
                Map<String, Object> params = buildCommonParams(fromTs, toExcl, dateFromInclDisplay, dateToInclDisplay);
                notiService.exportReportNotiAsync(req.projectId(), "Export Success!");
                return generateTrueExcel(req, params);
            } catch (Exception e) {
                log.error("Generate Excel failed: {}", e.getMessage(), e);
                throw new RuntimeException("Generate Excel failed", e);
            }
        }

        // ---- PDF/DOCX/PPTX: สร้างไฟล์ย่อย แล้ว ZIP รวม ----
        try {
            // เก็บชื่อไฟล์กับเนื้อไฟล์แต่ละ section
            record SectionFile(String name, byte[] bytes) {}
            List<SectionFile> files = new ArrayList<>();

            // QualityGateSummary
            if (req.includeSections().contains("QualityGateSummary")) {
                byte[] b = generateQualityGateReport(req, fromTs, toExcl, dateFromInclDisplay, dateToInclDisplay, fmt);
                if (b != null && b.length > 0) {
                    files.add(new SectionFile("QualityGateSummary", b));
                }
            }

            // IssueBreakdown
            if (req.includeSections().contains("IssueBreakdown")) {
                byte[] b = generateIssueBreakdownReport(req, fromTs, toExcl, dateFromInclDisplay, dateToInclDisplay, fmt);
                if (b != null && b.length > 0) {
                    files.add(new SectionFile("IssueBreakdown", b));
                }
            }

            if (files.isEmpty()) {
                throw new IllegalStateException("No sections selected for export");
            }

            // ถ้ามีแค่ไฟล์เดียว ไม่ต้อง ZIP
            if (files.size() == 1) {
                notiService.exportReportNotiAsync(req.projectId(), "Export Success!");
                return files.get(0).bytes();
            }

            // ถ้ามีหลายไฟล์ ให้ ZIP
            try (ByteArrayOutputStream zipOutput = new ByteArrayOutputStream();
                 ZipOutputStream zipOutputs = new ZipOutputStream(zipOutput)) {

                String projectName = getProjectName(req, files.isEmpty() ? List.of() :
                        (files.get(0).name().equals("QualityGateSummary") ?
                                fetchSectionRows(req, "QualityGateSummary", fromTs, toExcl) :
                                fetchSectionRows(req, "IssueBreakdown", fromTs, toExcl)));

                for (SectionFile f : files) {
                    // ตั้งชื่อไฟล์: QualityGateSummary_ProjectName.pdf
                    String entryName = f.name() + "_" + projectName + "." + fmt;
                    zipOutputs.putNextEntry(new ZipEntry(entryName));
                    zipOutputs.write(f.bytes());
                    zipOutputs.closeEntry();
                }
                zipOutputs.finish();

                notiService.exportReportNotiAsync(req.projectId(), "Export Success!");
                return zipOutput.toByteArray();
            }

        } catch (Exception e) {
            log.error("Generate report failed: {}", e.getMessage(), e);
            throw new RuntimeException("Generate report failed", e);
        }

    }

    public String getProjectNameForExport(UUID projectId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT name FROM projects WHERE project_id = ?",
                    String.class,
                    projectId
            );
        } catch (Exception e) {
            log.warn("Cannot get project name for project_id: {}", projectId);
            return "Unknown";
        }
    }

    private byte[] generateQualityGateReport(ReportRequest req, Timestamp fromTs, Timestamp toExcl,
                                             Date dateFromDisplay, Date dateToDisplay, String format) throws Exception {
        List<Map<String, ?>> qgateRows = fetchSectionRows(req, "QualityGateSummary", fromTs, toExcl);

        if (qgateRows.isEmpty()) {
            log.warn("No data found for QualityGateSummary");
            return new byte[0];
        }

        Map<String, Object> params = buildCommonParams(fromTs, toExcl, dateFromDisplay, dateToDisplay);

        // เพิ่ม project_name ให้ทุก row ถ้ายังไม่มี
        String projectName = getProjectName(req, qgateRows);
        for (Map<String, ?> row : qgateRows) {
            if (!row.containsKey("project_name") || row.get("project_name") == null) {
                ((Map<String, Object>) row).put("project_name", projectName);
            }
        }

        params.put("project_name", projectName);

        JRMapCollectionDataSource dataSource = new JRMapCollectionDataSource(qgateRows);

        try (InputStream jrxml = new ClassPathResource("reports/QualityGateReport.jrxml").getInputStream()) {
            JasperReport report = JasperCompileManager.compileReport(jrxml);
            JasperPrint print = JasperFillManager.fillReport(report, params, dataSource);
            return exportToFormat(print, format, req, params);
        }
    }

    private byte[] generateIssueBreakdownReport(ReportRequest req, Timestamp fromTs, Timestamp toExcl,
                                                Date dateFromDisplay, Date dateToDisplay, String format) throws Exception {
        List<Map<String, ?>> issueRows = fetchSectionRows(req, "IssueBreakdown", fromTs, toExcl);

        if (issueRows.isEmpty()) {
            log.warn("No data found for IssueBreakdown");
            return new byte[0];
        }

        Map<String, Object> params = buildCommonParams(fromTs, toExcl, dateFromDisplay, dateToDisplay);

        // เพิ่ม project_name ให้ทุก row
        String projectName = getProjectName(req, issueRows);
        for (Map<String, ?> row : issueRows) {
            ((Map<String, Object>) row).put("project_name", projectName);
        }

        params.put("project_name", projectName);

        JRMapCollectionDataSource dataSource = new JRMapCollectionDataSource(issueRows);

        try (InputStream jrxml = new ClassPathResource("reports/IssueBreakdownReport.jrxml").getInputStream()) {
            JasperReport report = JasperCompileManager.compileReport(jrxml);
            JasperPrint print = JasperFillManager.fillReport(report, params, dataSource);
            return exportToFormat(print, format, req, params);
        }
    }

    private Map<String, Object> buildCommonParams(Timestamp fromTs, Timestamp toExcl,
                                                  Date dateFromDisplay, Date dateToDisplay) {
        Map<String, Object> params = new HashMap<>();
        params.put("dateNow", new java.util.Date());
        params.put("DATE_FROM_TS", fromTs);
        params.put("DATE_TO_TS_EXCL", toExcl);
        params.put("DATE_FROM_DISPLAY", dateFromDisplay);
        params.put("DATE_TO_DISPLAY", dateToDisplay);
        return params;
    }

    private String getProjectName(ReportRequest req, List<Map<String, ?>> rows) {
        // ลองดึงจาก row แรก
        if (!rows.isEmpty()) {
            Object v = rows.get(0).get("project_name");
            if (v != null) return String.valueOf(v);
        }

        // ถ้าไม่มีให้ query จาก database
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT name FROM projects WHERE project_id = ?",
                    String.class,
                    req.projectId()
            );
        } catch (Exception e) {
            log.warn("Cannot get project name for project_id: {}", req.projectId());
            return "(unknown)";
        }
    }

    private List<Map<String, ?>> fetchSectionRows(ReportRequest req, String section,
                                                  Timestamp fromTs, Timestamp toExcl)  {
        List<String> selectedCols = switch (section) {
                case "QualityGateSummary" -> List.of("started_at", "quality_gate",
                        "bugs", "coverage", "code_smells", "vulnerabilities", "duplicated_lines_density",
                        "maintainability_gate", "reliability_gate", "security_gate", "security_review_gate");
                case "IssueBreakdown" -> List.of("component", "message", "type", "severity", "status",
                        "created_at", "due_date", "assigned_to");
                default -> List.of("project_id", "started_at");
        };

        // ตรวจสอบว่าต้อง JOIN กับ projects หรือไม่
        boolean needsJoinProjects = selectedCols.contains("project_name");
        boolean needsJoinUsers = selectedCols.contains("assigned_to");
        final List<String> colsForMap = List.copyOf(selectedCols);

        String selectList = colsForMap.stream()
                .map(c -> {
                    String expr = getColumnExpression(c, section);
                    if (expr == null) {
                        throw new IllegalArgumentException("Unknown column: " + c + " for section: " + section);
                    }
                    return expr;
                })
                .collect(Collectors.joining(", "));

        String sql = switch (section) {
            case "QualityGateSummary" ->
                    "SELECT " + selectList +
                            " FROM scans s " +
                            (needsJoinProjects ? "JOIN projects p ON p.project_id = s.project_id " : "") +
                            "WHERE s.project_id = ? AND s.started_at >= ? AND s.started_at < ? " +
                            "ORDER BY s.started_at DESC";
            case "IssueBreakdown" ->
                    "SELECT " + selectList +
                            " FROM issues i " +
                            " JOIN scans s ON s.scan_id = i.scan_id " +
                            (needsJoinProjects ? "JOIN projects p ON p.project_id = s.project_id " : "") +
                            (needsJoinUsers ? "LEFT JOIN users u ON u.user_id = i.assigned_to " : "") +
                            " WHERE s.project_id = ? AND s.started_at >= ? AND s.started_at < ? " +
                            " ORDER BY s.started_at DESC, i.severity DESC";
            default ->
                    "SELECT " + selectList + " FROM scans s WHERE s.project_id = ? AND s.started_at >= ? AND s.started_at < ?";
        };

        return jdbcTemplate.query(con -> {
            PreparedStatement ps = con.prepareStatement(sql);
            ps.setObject(1, req.projectId());
            ps.setTimestamp(2, fromTs);
            ps.setTimestamp(3, toExcl);
            return ps;
        }, (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            for (String c : colsForMap) {
                m.put(c, rs.getObject(c));
            }
            return m;
        });
    }

    private String getColumnExpression(String columnName, String section) {
        // สำหรับ columns ที่ใช้ร่วมกันได้
        if ("project_name".equals(columnName)) {
            return "p.name AS project_name";
        }

        // สำหรับแต่ละ section
        return switch (section) {
            case "QualityGateSummary" -> switch (columnName) {
                case "project_id" -> "s.project_id AS project_id";
                case "started_at" -> "s.started_at AS started_at";
                case "quality_gate" -> "s.quality_gate AS quality_gate";
                case "maintainability_gate" -> "s.maintainability_gate AS maintainability_gate";
                case "reliability_gate" -> "s.reliability_gate AS reliability_gate";
                case "security_gate" -> "s.security_gate AS security_gate";
                case "security_review_gate" -> "s.security_review_gate AS security_review_gate";
                case "bugs" -> "(s.metrics->>'bugs')::int AS bugs";
                case "coverage" -> "(s.metrics->>'coverage')::numeric AS coverage";
                case "code_smells" -> "(s.metrics->>'code_smells')::int AS code_smells";
                case "vulnerabilities" -> "(s.metrics->>'vulnerabilities')::int AS vulnerabilities";
                case "duplicated_lines_density" -> "(s.metrics->>'duplicated_lines_density')::numeric AS duplicated_lines_density";
                default -> null;
            };
            case "IssueBreakdown" -> switch (columnName) {
                case "created_at"   -> "i.created_at AS created_at";
                case "due_date"     -> "i.due_date AS due_date";
                case "type"         -> "i.type AS type";
                case "severity"     -> "i.severity AS severity";
                case "component"    -> "i.component AS component";
                case "message"      -> "i.message AS message";
                case "status"       -> "i.status AS status";
                case "assigned_to"  -> "COALESCE(u.email, i.assigned_to::text) AS assigned_to";
                default -> null;
            };
            default -> null;
        };
    }


    private byte[] exportToFormat(JasperPrint print, String format, ReportRequest req, Map<String, Object> params) throws JRException, IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            switch (format) {
                case "pdf" -> {
                    JRPdfExporter ex = new JRPdfExporter();
                    ex.setExporterInput(new SimpleExporterInput(print));
                    ex.setExporterOutput(new SimpleOutputStreamExporterOutput(bos));
                    ex.exportReport();
                }
                case "xlsx" -> {
                    byte[] excelBytes = generateTrueExcel(req, params);
                    bos.write(excelBytes);
                }
                case "docx" -> {
                    JRDocxExporter ex = new JRDocxExporter();
                    ex.setExporterInput(new SimpleExporterInput(print));
                    ex.setExporterOutput(new SimpleOutputStreamExporterOutput(bos));
                    ex.exportReport();
                }
                case "pptx" -> {
                    JRPptxExporter ex = new JRPptxExporter();
                    ex.setExporterInput(new SimpleExporterInput(print));
                    ex.setExporterOutput(new SimpleOutputStreamExporterOutput(bos));
                    ex.exportReport();
                }
                default -> throw new IllegalArgumentException("Unsupported format: " + format);
            }
            return bos.toByteArray();
        }
    }


    //generateExcel
    private byte[] generateTrueExcel(ReportRequest req, Map<String, Object> params) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            String projectName = getProjectName(req, List.of());
            // === QualityGateSummary ===
            if (req.includeSections().contains("QualityGateSummary")) {
                Sheet sheet = workbook.createSheet("Quality Gate");
                List<Map<String, ?>> rows = fetchSectionRows(
                        req,
                        "QualityGateSummary",
                        (Timestamp) params.get("DATE_FROM_TS"),
                        (Timestamp) params.get("DATE_TO_TS_EXCL")
                );
                writeSheetWithHeader(sheet, rows, "Quality Gate Summary", projectName,
                        (Date) params.get("DATE_FROM_DISPLAY"),
                        (Date) params.get("DATE_TO_DISPLAY"));
            }

            // === IssueBreakdown ===
            if (req.includeSections().contains("IssueBreakdown")) {
                Sheet sheet = workbook.createSheet("Issue Breakdown");
                List<Map<String, ?>> rows = fetchSectionRows(
                        req,
                        "IssueBreakdown",
                        (Timestamp) params.get("DATE_FROM_TS"),
                        (Timestamp) params.get("DATE_TO_TS_EXCL")
                );
                writeSheetWithHeader(sheet, rows, "Issue Breakdown", projectName,
                        (Date) params.get("DATE_FROM_DISPLAY"),
                        (Date) params.get("DATE_TO_DISPLAY"));
            }

            try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                workbook.write(bos);
                return bos.toByteArray();
            }
        }
    }

    private void writeSheetWithHeader(Sheet sheet, List<Map<String, ?>> rows, String sectionTitle,
                                      String projectName, Date dateFrom, Date dateTo) {
        int currentRow = 0;

        // Title
        sheet.createRow(currentRow++).createCell(0).setCellValue(sectionTitle);
        currentRow++; // บรรทัดว่าง

        // Project Name
        Row projectRow = sheet.createRow(currentRow++);
        projectRow.createCell(0).setCellValue("Project Name :");
        projectRow.createCell(1).setCellValue(projectName);

        // วันที่
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMMM yyyy", new Locale("th", "TH"));
        Row dateRow = sheet.createRow(currentRow++);
        dateRow.createCell(0).setCellValue("เริ่มจากวันที่");
        dateRow.createCell(1).setCellValue(sdf.format(dateFrom));
        dateRow.createCell(2).setCellValue("ถึง");
        dateRow.createCell(3).setCellValue(sdf.format(dateTo));

        currentRow += 1;

        if (rows.isEmpty()) return;

        // ตารางข้อมูล - Header
        Row headerRow = sheet.createRow(currentRow++);
        List<String> cols = new ArrayList<>(rows.get(0).keySet());
        headerRow.createCell(0).setCellValue("No.");
        for (int i = 0; i < cols.size(); i++) {
            headerRow.createCell(i+1).setCellValue(cols.get(i));
        }

        // ตารางข้อมูล - Body
        int rowNum = 1;
        for (Map<String, ?> data : rows) {
            Row row = sheet.createRow(currentRow++);
            row.createCell(0).setCellValue(rowNum++);
            for (int c = 0; c < cols.size(); c++) {
                Object v = data.get(cols.get(c));
                Cell cell = row.createCell(c + 1);
                if (v instanceof Number num) {
                    cell.setCellValue(num.doubleValue());
                } else if (v instanceof Date date) {
                    cell.setCellValue(date.toString());
                } else {
                    cell.setCellValue(v != null ? v.toString() : "");
                }
            }
        }

        // Auto size
        sheet.autoSizeColumn(0);
        for (int i = 0; i < cols.size(); i++) {
            sheet.autoSizeColumn(i+1);
        }
    }
    //GenerateExcel
}
