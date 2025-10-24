package com.automate.CodeReview.Service;


import com.automate.CodeReview.dto.ReportRequest;
import com.automate.CodeReview.repository.ScansRepository;
import lombok.extern.slf4j.Slf4j;
import net.sf.jasperreports.engine.*;

import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import net.sf.jasperreports.engine.export.JRPdfExporter;
import net.sf.jasperreports.engine.export.ooxml.JRDocxExporter;
import net.sf.jasperreports.engine.export.ooxml.JRPptxExporter;
import net.sf.jasperreports.engine.export.ooxml.JRXlsxExporter;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import net.sf.jasperreports.export.SimpleXlsxReportConfiguration;
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

import javax.sql.DataSource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Map.entry;

@Service
@Slf4j
public class ExportService {

    private final ScansRepository scansRepository;
    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    public ExportService(ScansRepository scansRepository, DataSource dataSource, JdbcTemplate jdbcTemplate) {
        this.scansRepository = scansRepository;
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
    }

    public byte[] generateReport(ReportRequest req) {
        ZoneId Z = ZoneId.of("Asia/Bangkok");
        Timestamp fromTs = Timestamp.from(req.dateFrom().atStartOfDay(Z).toInstant());
        Timestamp toExcl = Timestamp.from(req.dateTo().plusDays(1).atStartOfDay(Z).toInstant());

        Date dateFromInclDisplay = Date.from(req.dateFrom().atStartOfDay(Z).toInstant());
        Date dateToInclDisplay   = Date.from(req.dateTo().atStartOfDay(Z).toInstant());

        Map<String, Object> params = new HashMap<>();
        params.put("dateNow", new java.util.Date());
        params.put("DATE_FROM_TS", fromTs);
        params.put("DATE_TO_TS_EXCL", toExcl);
        params.put("DATE_FROM_DISPLAY", dateFromInclDisplay);
        params.put("DATE_TO_DISPLAY", dateToInclDisplay);

        // ====================== Section: Quality Gate ======================
        if (req.includeSections().contains("QualityGateSummary")) {
            List<Map<String, ?>> qgateRows = fetchSectionRows(req, "QualityGateSummary", fromTs, toExcl);
            JRMapCollectionDataSource qgateDS = new JRMapCollectionDataSource(qgateRows);
            params.put("INCLUDE_QUALITY_GATE", true);
            params.put("QGATE_DS", qgateDS);

            // ----- set ค่าให้ Page Header -----
            String projectName = null;
            if (!qgateRows.isEmpty()) {
                Object v = qgateRows.get(0).get("project_name");
                if (v != null) projectName = String.valueOf(v);
            }
            // ถ้า selectedCols ไม่ได้ใส่ project_name มา ให้ดึงชื่อโปรเจ็กต์จากตาราง projects
            if (projectName == null) {
                try {
                    projectName = jdbcTemplate.queryForObject(
                            "SELECT name FROM projects WHERE project_id = ?",
                            String.class,
                            req.projectId()
                    );
                } catch (Exception ignore) { /* keep null */ }
            }
            params.put("project_name", projectName != null ? projectName : "(unknown)");
        } else {
            params.put("INCLUDE_QUALITY_GATE", false);
        }

        // ====================== Section: Issue Breakdown ======================
        if (req.includeSections().contains("IssueBreakdown")) {
            List<Map<String, ?>> issueRows = fetchSectionRows(req, "IssueBreakdown", fromTs, toExcl);
            JRMapCollectionDataSource issueDS = new JRMapCollectionDataSource(issueRows);
            params.put("INCLUDE_ISSUE_BREAKDOWN", true);
            params.put("ISSUE_DS", issueDS);
        } else {
            params.put("INCLUDE_ISSUE_BREAKDOWN", false);
        }

        // ====================== Section: Security Analysis ======================
        if (req.includeSections().contains("SecurityAnalysis")) {
            List<Map<String, ?>> secRows = fetchSectionRows(req, "SecurityAnalysis", fromTs, toExcl);
            JRMapCollectionDataSource secDS = new JRMapCollectionDataSource(secRows);
            params.put("INCLUDE_SECURITY_ANALYSIS", true);
            params.put("SECURITY_DS", secDS);
        } else {
            params.put("INCLUDE_SECURITY_ANALYSIS", false);
        }

        // ====================== Section: Technical Debt ======================
        if (req.includeSections().contains("TechnicalDebt")) {
            List<Map<String, ?>> debtRows = fetchSectionRows(req, "TechnicalDebt", fromTs, toExcl);
            JRMapCollectionDataSource debtDS = new JRMapCollectionDataSource(debtRows);
            params.put("INCLUDE_TECHNICAL_DEBT", true);
            params.put("TECH_DS", debtDS);
        } else {
            params.put("INCLUDE_TECHNICAL_DEBT", false);
        }
        log.info("includeSections = {}", req.includeSections());
        log.info("DATE_FROM_TS = {}, DATE_TO_TS_EXCL = {}", fromTs, toExcl);

        // ==== compile & fill report ====
        try (InputStream jrxml = new ClassPathResource("reports/QualityGateReport.jrxml").getInputStream()) {
            JasperReport report = JasperCompileManager.compileReport(jrxml);
            JasperPrint print = JasperFillManager.fillReport(report, params, new JREmptyDataSource());
            return exportBytes(print, req.outputFormat(), req, params);
        } catch (Exception e) {
            log.error("Generate report failed: {}", e.getMessage(), e);
            throw new RuntimeException("Generate report failed", e);
        }
    }

    private List<Map<String, ?>> fetchSectionRows(ReportRequest req, String section,
                                                  Timestamp fromTs, Timestamp toExcl)  {
        List<String> requested = Optional.ofNullable(req.selectedColumns())
                .map(m -> m.get(section)).orElse(null);

        List<String> allowed = new ArrayList<>(allowedColumn.getOrDefault(section, Set.of()));
        List<String> selectedCols = (requested == null)
                ? List.of()
                : requested.stream().filter(allowed::contains).toList();

        if (selectedCols.isEmpty()) {
            selectedCols = List.of("project_id", "started_at", "quality_gate"); // หรือ default ของ section นั้น ๆ
        }

        boolean needsJoinProjects = selectedCols.contains("project_name");
        final List<String> colsForMap = List.copyOf(selectedCols);

        String selectList = colsForMap.stream()
                .map(c -> Optional.ofNullable(COL_EXPR.get(c))
                        .orElseThrow(() -> new IllegalArgumentException("Unknown column: " + c)))
                .collect(Collectors.joining(", "));

        String sql = switch (section) {
            case "QualityGateSummary" ->
                    "SELECT " + selectList +
                            " FROM scans s " +
                            (needsJoinProjects ? "JOIN projects p ON p.project_id = s.project_id " : "") +
                            "WHERE s.project_id = ? AND s.started_at >= ? AND s.started_at < ? " +
                            "ORDER BY s.started_at DESC";
            case "IssueBreakdown" ->
                    "SELECT " + selectList + " FROM issues WHERE project_id = ? AND created_at BETWEEN ? AND ?";
            case "SecurityAnalysis" ->
                    "SELECT " + selectList + " FROM vulnerabilities WHERE project_id = ? AND created_at BETWEEN ? AND ?";
            case "TechnicalDebt" ->
                    "SELECT " + selectList + " FROM tech_debts WHERE project_id = ? AND started_at BETWEEN ? AND ?";
            default ->
                    "SELECT " + selectList + " FROM scans s WHERE s.project_id = ? AND s.started_at BETWEEN ? AND ?";
        };

        log.info("[{}] selectedCols = {}", section, colsForMap);
        log.info("[{}] SQL = {}", section, sql);
        log.info("[{}] params: projectId={}, fromTs={}, toExcl={}",
                section, req.projectId(), fromTs, toExcl);
        return jdbcTemplate.query(con -> {
            PreparedStatement ps = con.prepareStatement(sql);
            ps.setObject(1, req.projectId());
            ps.setTimestamp(2, fromTs);
            ps.setTimestamp(3, toExcl);
            return ps;
        }, (rs, i) -> {
            Map<String, Object> m = new HashMap<>();
            for (String c : colsForMap) {
                m.put(c, rs.getObject(c));
            }
            return m;
        });
    }

    private static final Map<String, Set<String>> allowedColumn = Map.of(
            "QualityGateSummary", Set.of("project_name","started_at","quality_gate","bugs","coverage","code_smells","vulnerabilities","duplicated_lines_density","maintainability_gate","reliability_gate","security_gate","security_review_gate"),
            "IssueBreakdown",     Set.of("project_id","created_at","type","severity","component","message","status","assign_to"),
            "SecurityAnalysis",   Set.of("vuln_id","severity","cvss","component","status")
            //เดี๋ยวเพิ่มอันอื่น
    );

    private static List<String> sanitize(String section, List<String> requested) {
        Set<String> allowed = allowedColumn.getOrDefault(section, Set.of());
        if (requested == null) return List.of();
        return requested.stream().filter(allowed::contains).toList();
    }


    private byte[] exportBytes(JasperPrint print, String format, ReportRequest req, Map<String, Object> params)
            throws JRException, IOException {
        String fmt = (format == null ? "pdf" : format.toLowerCase());
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            switch (fmt) {
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
                default -> throw new IllegalArgumentException("Unsupported format: " + fmt);
            }
            return bos.toByteArray();
        }
    }

    private static final Map<String,String> COL_EXPR = Map.ofEntries(
            // จาก projects
            Map.entry("project_name", "p.name AS project_name"),
            // จาก scans (ใส่ s. ทุกอัน)
            Map.entry("started_at", "s.started_at AS started_at"),
            Map.entry("quality_gate", "s.quality_gate AS quality_gate"),
            Map.entry("maintainability_gate", "s.maintainability_gate AS maintainability_gate"),
            Map.entry("reliability_gate", "s.reliability_gate AS reliability_gate"),
            Map.entry("security_gate", "s.security_gate AS security_gate"),
            Map.entry("security_review_gate", "s.security_review_gate AS security_review_gate"),
            // metrics (JSONB) → ดึงด้วย ->> แล้ว CAST พร้อม alias ให้ตรง JRXML
            Map.entry("bugs", "(s.metrics->>'bugs')::int AS bugs"),
            Map.entry("coverage", "(s.metrics->>'coverage')::numeric AS coverage"),
            Map.entry("code_smells", "(s.metrics->>'code_smells')::int AS code_smells"),
            Map.entry("vulnerabilities", "(s.metrics->>'vulnerabilities')::int AS vulnerabilities"),
            Map.entry("duplicated_lines_density", "(s.metrics->>'duplicated_lines_density')::numeric AS duplicated_lines_density")
    );



    //generateExcel
    private byte[] generateTrueExcel(ReportRequest req, Map<String, Object> params) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {

            // === QualityGateSummary ===
            if (req.includeSections().contains("QualityGateSummary")) {
                Sheet sheet = workbook.createSheet("Quality Gate");
                List<Map<String, ?>> rows = fetchSectionRows(
                        req,
                        "QualityGateSummary",
                        (Timestamp) params.get("DATE_FROM_TS"),
                        (Timestamp) params.get("DATE_TO_TS_EXCL")
                );
                writeSheet(sheet, rows);
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
                writeSheet(sheet, rows);
            }

            // === SecurityAnalysis ===
            if (req.includeSections().contains("SecurityAnalysis")) {
                Sheet sheet = workbook.createSheet("Security Analysis");
                List<Map<String, ?>> rows = fetchSectionRows(
                        req,
                        "SecurityAnalysis",
                        (Timestamp) params.get("DATE_FROM_TS"),
                        (Timestamp) params.get("DATE_TO_TS_EXCL")
                );
                writeSheet(sheet, rows);
            }

            // === TechnicalDebt ===
            if (req.includeSections().contains("TechnicalDebt")) {
                Sheet sheet = workbook.createSheet("Technical Debt");
                List<Map<String, ?>> rows = fetchSectionRows(
                        req,
                        "TechnicalDebt",
                        (Timestamp) params.get("DATE_FROM_TS"),
                        (Timestamp) params.get("DATE_TO_TS_EXCL")
                );
                writeSheet(sheet, rows);
            }

            try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                workbook.write(bos);
                return bos.toByteArray();
            }
        }
    }

    private void writeSheet(Sheet sheet, List<Map<String, ?>> rows) {
        if (rows.isEmpty()) return;

        // header
        Row header = sheet.createRow(0);
        List<String> cols = new ArrayList<>(rows.get(0).keySet());
        for (int i = 0; i < cols.size(); i++) {
            header.createCell(i).setCellValue(cols.get(i));
        }

        // body
        for (int r = 0; r < rows.size(); r++) {
            Row row = sheet.createRow(r + 1);
            Map<String, ?> data = rows.get(r);
            for (int c = 0; c < cols.size(); c++) {
                Object v = data.get(cols.get(c));
                Cell cell = row.createCell(c);
                if (v instanceof Number num) {
                    cell.setCellValue(num.doubleValue());
                } else if (v instanceof java.util.Date date) {
                    cell.setCellValue(date.toString());
                } else {
                    cell.setCellValue(v != null ? v.toString() : "");
                }
            }
        }

        // auto size columns
        for (int i = 0; i < cols.size(); i++) {
            sheet.autoSizeColumn(i);
        }
    }
    //GenerateExcel
}
