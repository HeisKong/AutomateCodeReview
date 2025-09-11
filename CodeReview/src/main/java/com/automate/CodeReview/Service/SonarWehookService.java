package com.automate.CodeReview.Service;

import com.automate.CodeReview.dto.SonarWebhookPayload;
import com.automate.CodeReview.entity.ProjectsEntity;
import com.automate.CodeReview.entity.ScansEntity;
import com.automate.CodeReview.exception.InvalidWebhookPayloadException;
import com.automate.CodeReview.exception.InvalidWebhookSignatureException;
import com.automate.CodeReview.exception.SonarApiException;
import com.automate.CodeReview.repository.ProjectsRepository;
import com.automate.CodeReview.repository.ScansRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
public class SonarWehookService {
    private final ProjectsRepository projectsRepository;
    private final ScansRepository scansRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public SonarWehookService(ProjectsRepository projectsRepository, ScansRepository scansRepository) {
        this.projectsRepository = projectsRepository;
        this.scansRepository = scansRepository;
    }

    @Value("${sonar.host-url}")
    private String sonarHostUrl;

    @Value("${sonar.metrics:bugs,vulnerabilities,code_smells,coverage,duplicated_lines_density}")
    private String metricsCsv;

    @Value("${sonar.service-token}")
    private String serviceToken;

    @Value("${sonar.webhook.secret:}")
    private String webhookSecret;

    /* ---------- HMAC: header = X-Sonar-Webhook-HMAC-SHA256 (hex ตัวเล็ก) ---------- */
    public boolean verifyHmac(byte[] rawBody, String headerHex) {
        if (webhookSecret == null || webhookSecret.isBlank()) return true;
        if (headerHex == null || headerHex.isBlank()) {
            throw new InvalidWebhookSignatureException();
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] sig = mac.doFinal(rawBody);
            String calcHex = toHexLower(sig);
            if (!constantTimeEquals(calcHex, headerHex)) {
                throw new InvalidWebhookSignatureException();
            }
            return true;
        } catch (Exception e) {
            throw new InvalidWebhookSignatureException();
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

    /* ---------- Parse ---------- */
    public SonarWebhookPayload parse(byte[] body) {
        try {
            return objectMapper.readValue(body, SonarWebhookPayload.class);
        } catch (Exception e) {
            throw new InvalidWebhookPayloadException(e.getMessage());
        }
    }

    /* ---------- Async worker ---------- */
    @Async("webhookExecutor")
    public void processAsync(SonarWebhookPayload p, String projectHeader, String deliveryId) {
        try {
            process(p, projectHeader, deliveryId);
        } catch (Exception e) {
            log.error("webhook processAsync error: {}", e.toString(), e);
        }
    }

    private void process(SonarWebhookPayload p, String projectHeader, String deliveryId) throws Exception {
        String projectKey = (p.getProject() != null && p.getProject().getKey() != null)
                ? p.getProject().getKey()
                : projectHeader;
        String branch = (p.getBranch() != null) ? p.getBranch().getName() : null;
        String taskId = p.getTaskId();

        if (projectKey == null || taskId == null) {
            throw new InvalidWebhookPayloadException("Missing projectKey or taskId in webhook payload");
        }

        // 1) taskId -> analysisId
        String analysisId = fetchAnalysisId(taskId);

        // 2) QG ณ analysis นั้น
        String qualitygate = (analysisId != null) ? fetchQGByAnalysis(analysisId) : "UNKNOWN";
        if (p.getQualityGate()!=null && p.getQualityGate().getStatus()!=null) {
            qualitygate = p.getQualityGate().getStatus().toUpperCase(Locale.ROOT);
        }

        // 3) Metrics ล่าสุดของโปรเจ็กต์ (ถ้า webhook มี branch ก็ส่ง branch ไปด้วย)
        Map<String,Object> metrics = fetchMetrics(projectKey, branch);

        // 4) บันทึก automateDB (ตัวอย่าง: สร้างแถวใหม่; ถ้าต้องการ update แถว SUBMITTED ให้ปรับตาม repo ของคุณ)
        ProjectsEntity project = projectsRepository.findBySonarProjectKey(projectKey)
                .orElseGet(() -> {
                    ProjectsEntity pe = new ProjectsEntity();
                    pe.setSonarProjectKey(projectKey);
                    pe.setName(projectKey);
                    return projectsRepository.save(pe);
                });

        ScansEntity entity = new ScansEntity();
        entity.setProject(project);
        entity.setStatus("SUCCESS");           // รอบนี้คือ ‘ผลวิเคราะห์ออกแล้ว’
        entity.setQualityGate(qualitygate);
        entity.setMetrics(metrics);
        entity.setStartedAt(LocalDateTime.now());
        entity.setCompletedAt(LocalDateTime.now());
        entity.setLogFilePath("- (webhook)");
        scansRepository.save(entity);

        log.info("Webhook stored: project={}, branch={}, qualitygate={}, delivery={}", projectKey, branch, qualitygate, deliveryId);
    }

    private String fetchAnalysisId(String taskId) {
        try {
            String url = sonarHostUrl + "/api/ce/task?id=" + URLEncoder.encode(taskId, StandardCharsets.UTF_8);
            String json = httpGet(url);
            JsonNode n = objectMapper.readTree(json);
            return n.path("task").path("analysisId").asText(null);
        } catch (Exception e) {
            throw new SonarApiException("Failed to fetch analysisId for taskId=" + taskId + ": " + e.getMessage());
        }
    }

    private String fetchQGByAnalysis(String analysisId) {
        if (analysisId == null || analysisId.isBlank()) {
            throw new SonarApiException("analysisId is blank");
        }
        try {
            String url = sonarHostUrl
                    + "/api/qualitygates/project_status?analysisId="
                    + URLEncoder.encode(analysisId, StandardCharsets.UTF_8);
            JsonNode root = readJson(url); // helper อ่าน JSON ด้านล่าง
            String status = root.path("projectStatus").path("status").asText(null);
            return (status != null && !status.isBlank())
                    ? status.toUpperCase(Locale.ROOT)
                    : "UNKNOWN";
        } catch (Exception e) {
            throw new SonarApiException("Failed to fetch quality gate for analysisId="
                    + analysisId + " :: " + e.getMessage());
        }
    }

    private Map<String, Object> fetchMetrics(String projectKey, String branch) {
        if (projectKey == null || projectKey.isBlank()) {
            throw new SonarApiException("projectKey is blank");
        }
        if (metricsCsv == null || metricsCsv.isBlank()) {
            throw new SonarApiException("metricsCsv is blank (check config 'sonar.metrics')");
        }

        try {
            StringBuilder url = new StringBuilder()
                    .append(sonarHostUrl)
                    .append("/api/measures/component?component=")
                    .append(URLEncoder.encode(projectKey, StandardCharsets.UTF_8))
                    .append("&metricKeys=")
                    .append(URLEncoder.encode(metricsCsv, StandardCharsets.UTF_8));

            if (branch != null && !branch.isBlank()) {
                url.append("&branch=").append(URLEncoder.encode(branch, StandardCharsets.UTF_8));
            }

            JsonNode root = readJson(url.toString());
            JsonNode measures = root.path("component").path("measures");
            if (!measures.isArray()) {
                throw new SonarApiException("measures array not found for project=" + projectKey
                        + (branch != null ? " branch=" + branch : ""));
            }

            Map<String, Object> result = new LinkedHashMap<>();
            for (JsonNode m : measures) {
                String metric = m.path("metric").asText(null);
                if (metric == null || metric.isBlank()) continue;
                JsonNode v = m.get("value");
                result.put(metric, (v != null && !v.isNull()) ? v.asText() : "N/A");
            }
            return result;
        } catch (Exception e) {
            throw new SonarApiException("Failed to fetch metrics for project=" + projectKey
                    + (branch != null ? " branch=" + branch : "") + " :: " + e.getMessage());
        }
    }

    /** Helper: GET แล้ว parse เป็น JsonNode (httpGet ภายในควรโยน SonarApiException อยู่แล้ว) */
    private JsonNode readJson(String url) {
        String json = httpGet(url);
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new SonarApiException("Invalid JSON from Sonar API: " + e.getMessage());
        }
    }


    private String httpGet(String url) {
        try {
            String basic = Base64.getEncoder().encodeToString((serviceToken + ":").getBytes(StandardCharsets.UTF_8));
            var con = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            con.setRequestMethod("GET");
            con.setRequestProperty("Authorization", "Basic " + basic);
            con.setConnectTimeout(25000);
            con.setReadTimeout(60000);

            int code = con.getResponseCode();
            var is = (code >= 200 && code < 300) ? con.getInputStream() : con.getErrorStream();
            String body = (is != null) ? new String(is.readAllBytes(), StandardCharsets.UTF_8) : "";

            if (code < 200 || code >= 300) {
                throw new SonarApiException("Sonar API call failed: HTTP " + code + " " + url + " :: " + body);
            }

            return body;
        } catch (Exception e) {
            throw new SonarApiException("Sonar API call error: " + e.getMessage());
        }
    }



}
