package com.automate.CodeReview.Service;

import com.automate.CodeReview.Config.SonarProperties;
import com.automate.CodeReview.dto.SonarWebhookPayload;
import com.automate.CodeReview.entity.IssuesEntity;
import com.automate.CodeReview.entity.ProjectsEntity;
import com.automate.CodeReview.entity.ScansEntity;
import com.automate.CodeReview.repository.IssuesRepository;
import com.automate.CodeReview.repository.ProjectsRepository;
import com.automate.CodeReview.repository.ScansRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.*;
import java.util.stream.Collectors;

import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME;

@Service
@Slf4j
public class SonarWehookService {
    private final ProjectsRepository projectsRepository;
    private final ScansRepository scansRepository;
    private final IssuesRepository issuesRepository;
    private final WebClient sonarWebClient;
    private final SonarProperties props;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public SonarWehookService(ProjectsRepository projectsRepository, ScansRepository scansRepository,  IssuesRepository issuesRepository, SonarProperties props, WebClient sonarWebClient) {
        this.projectsRepository = projectsRepository;
        this.scansRepository = scansRepository;
        this.issuesRepository = issuesRepository;
        this.sonarWebClient = sonarWebClient;
        this.props = props;
    }

    @Value("${sonar.host-url}")
    private String sonarHostUrl;

    @Value("${sonar.metrics:bugs,vulnerabilities,code_smells,coverage,duplicated_lines_density}")
    private String metricsCsv;

    @Value("${sonar.service-token}")
    private String serviceToken;

    @Value("${sonar.webhook.secret:}")
    private String webhookSecret;

    /* ---------------- HMAC verify (ถ้าตั้ง secret) ---------------- */
    public boolean verifyHmac(byte[] rawBody, @Nullable String headerHex) {
        String secret = props.getWebhookSecret();
        if (secret == null || secret.isBlank()) return true;
        if (headerHex == null || headerHex.isBlank()) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] sig = mac.doFinal(rawBody);
            String calcHex = toHexLower(sig);
            return constantTimeEquals(calcHex, headerHex);
        } catch (Exception e) {
            log.warn("HMAC error: {}", e.toString());
            return false;
        }
    }
    private static String toHexLower(byte[] bytes) {
        char[] HEX = "0123456789abcdef".toCharArray();
        char[] out = new char[bytes.length * 2];
        for (int i = 0, j = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            out[j++] = HEX[v >>> 4];
            out[j++] = HEX[v & 0x0F];
        }
        return new String(out);
    }
    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        int diff = a.length() ^ b.length();
        for (int i = 0; i < Math.min(a.length(), b.length()); i++) diff |= a.charAt(i) ^ b.charAt(i);
        return diff == 0;
    }

    /* ---------------- Parse ---------------- */
    public @Nullable SonarWebhookPayload parse(byte[] body) {
        try { return objectMapper.readValue(body, SonarWebhookPayload.class); }
        catch (Exception e) { log.warn("parse webhook failed: {}", e.toString()); return null; }
    }

    /* ---------------- Entry (async) ---------------- */
    @Async("webhookExecutor")
    public void processAsync(SonarWebhookPayload p, String projectHeader, String deliveryId) {
        try { process(p, projectHeader, deliveryId); }
        catch (Exception e) { log.error("webhook process error: {}", e.toString(), e); }
    }

    /* ---------------- Main workflow ---------------- */
    private void process(SonarWebhookPayload p, String projectHeader, String deliveryId) throws Exception {
        String projectKey = (p.getProject()!=null && p.getProject().getKey()!=null)
                ? p.getProject().getKey() : projectHeader;
        String taskId    = p.getTaskId();
        String analysedAt = p.getAnalysedAt();  // <-- ใช้ชื่อ getter ของคุณจริง ๆ

        Instant when = parseSonarTimestamp(analysedAt);

        if (projectKey == null || taskId == null) {
            log.warn("webhook missing projectKey/taskId, delivery={}", deliveryId);
            return;
        }

        // 1) task -> analysisId
        String analysisId = fetchAnalysisId(taskId);

        // 2) Quality Gate ณ analysis นั้น (ถ้ามีใน payload เอาค่านั้นก่อน)
        String qg = (p.getQualityGate()!=null && p.getQualityGate().getStatus()!=null)
                ? p.getQualityGate().getStatus().toUpperCase(Locale.ROOT)
                : fetchQualityGate(analysisId);

        // 3) Metrics ล่าสุดของโปรเจ็กต์ (ถ้า payload บอก branch ก็แนบไป)
        Map<String,Object> metrics = fetchMetrics(projectKey);

        // 4) Upsert Project
        ProjectsEntity project = projectsRepository.findBySonarProjectKey(projectKey)
                .orElseGet(() -> {
                    ProjectsEntity pe = new ProjectsEntity();
                    pe.setSonarProjectKey(projectKey);
                    pe.setName(projectKey);
                    return projectsRepository.save(pe);
                });

        // 5) สร้างแถว Scan ใหม่ (หรือจะทำ idempotent ด้วย deliveryId/analysisId ก็ได้)
        ScansEntity scan = new ScansEntity();
        scan.setProject(project);
        scan.setStatus("SUCCESS");
        scan.setQualityGate(qg);
        scan.setMetrics(metrics);
        scan.setStartedAt(LocalDateTime.ofInstant(when, ZoneOffset.UTC));
        scan.setCompletedAt(LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC));
        scan.setLogFilePath("- (webhook)");
        scansRepository.save(scan);

        // 6) ดึง Issues ไล่หน้าแล้ว upsert ลงตาราง issues ผูกกับ scan นี้
        importIssues(projectKey, scan);

        log.info("Webhook stored: project={}, qg={}, delivery={}", projectKey, qg, deliveryId);
    }

    private static final DateTimeFormatter FLEX_OFFSET =
            new DateTimeFormatterBuilder()
                    .parseCaseInsensitive()
                    .append(ISO_LOCAL_DATE_TIME)
                    .optionalStart().appendOffset("+HH:MM", "+00:00").optionalEnd() // +07:00
                    .optionalStart().appendOffset("+HHMM", "+0000").optionalEnd()   // +0700
                    .optionalStart().appendOffset("+HH", "Z").optionalEnd()         // +07 หรือ Z
                    .toFormatter();

    private static Instant parseSonarTimestamp(String ts) {
        if (ts == null || ts.isBlank()) return Instant.now();
        return OffsetDateTime.parse(ts, FLEX_OFFSET).toInstant();
    }

    /* ---------------- Sonar calls ---------------- */

    private @Nullable String fetchAnalysisId(String taskId) {
        JsonNode n = sonarWebClient.get()
                .uri(uri -> uri.path("/api/ce/task").queryParam("id", taskId).build())
                .retrieve().bodyToMono(JsonNode.class).block();
        String id = (n!=null) ? n.path("task").path("analysisId").asText(null) : null;
        return (id!=null && !id.isBlank()) ? id : null;
    }

    private String fetchQualityGate(@Nullable String analysisId) {
        if (analysisId == null) return "UNKNOWN";
        JsonNode n = sonarWebClient.get()
                .uri(uri -> uri.path("/api/qualitygates/project_status")
                        .queryParam("analysisId", analysisId).build())
                .retrieve().bodyToMono(JsonNode.class).block();
        return (n!=null) ? n.path("projectStatus").path("status").asText("UNKNOWN") : "UNKNOWN";
    }

    private Map<String,Object> fetchMetrics(String projectKey) {
        Map<String,Object> result = new LinkedHashMap<>();
        try {
            JsonNode root = sonarWebClient.get()
                    .uri(uri -> uri.path("/api/measures/component")
                            .queryParam("component", projectKey)
                            .queryParam("metricKeys", sanitizeMetrics(props.getMetricsCsv(), metricsCsv))
                            .build())
                    .retrieve().bodyToMono(JsonNode.class).block();

            JsonNode measures = (root==null) ? null : root.path("component").path("measures");
            if (measures != null && measures.isArray()) {
                for (JsonNode m : measures) {
                    String k = m.path("metric").asText();
                    String v = m.hasNonNull("value") ? m.get("value").asText() : "N/A";
                    if (k != null && !k.isBlank()) result.put(k, v);
                }
            }
        } catch (Exception e) {
            result.put("error","fetch metrics failed");
            result.put("message", e.getMessage());
        }
        return result;
    }

    private static String sanitizeMetrics(String fromProps, String fallbackCsv) {
        String src = (fromProps != null && !fromProps.isBlank()) ? fromProps : fallbackCsv;
        String out = Arrays.stream(src.split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.joining(","));
        return out.isEmpty() ? "bugs" : out;
    }

    /* ---------------- Import Issues (paged) ---------------- */

    private void importIssues(String projectKey, ScansEntity scan) {
        int page = 1, ps = props.getPageSize();
        while (true) {
            final int p  = page;
            final int sz = ps;
            JsonNode resp = sonarWebClient.get()
                    .uri(uri -> uri.path("/api/issues/search")
                            .queryParam("projects", projectKey) // แก้จาก projectKeys -> projects
                            .queryParam("p", p)
                            .queryParam("ps", sz)
                            .build())
                    .retrieve().bodyToMono(JsonNode.class).block();

            JsonNode arr = (resp==null) ? null : resp.path("issues");
            if (arr == null || !arr.isArray() || arr.isEmpty()) break;

            arr.forEach(node -> upsertIssue(scan, node, projectKey));

            int total = (resp!=null) ? resp.path("paging").path("total").asInt(page*ps) : page*ps;
            if (page * ps >= total) break;
            page++;
        }
    }

    private void upsertIssue(ScansEntity scan, JsonNode i, String projectKey) {
        String issueProject = i.path("project").asText(""); // หรือ "projectKey" แล้วแต่เวอร์ชัน
        if (!projectKey.equals(issueProject)) return; // ข้ามถ้าไม่ตรง

        String key = i.path("key").asText();
        IssuesEntity entity = issuesRepository
                .findByScan_ScanIdAndIssueKey(scan.getScanId(), key)
                .orElseGet(IssuesEntity::new);

        entity.setScan(scan);
        entity.setIssueKey(key);
        entity.setType(i.path("type").asText());
        entity.setSeverity(i.path("severity").asText());
        entity.setComponent(i.path("component").asText());
        entity.setMessage(i.path("message").asText());
        entity.setStatus(i.path("status").asText());
        entity.setAssignedTo(null);

        issuesRepository.save(entity);
    }
}
