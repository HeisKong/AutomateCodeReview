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

    private final ObjectMapper objectMapper;

    public SonarWehookService(ProjectsRepository projectsRepository, ScansRepository scansRepository,  IssuesRepository issuesRepository, SonarProperties props, WebClient sonarWebClient , ObjectMapper objectMapper) {
        this.projectsRepository = projectsRepository;
        this.scansRepository = scansRepository;
        this.issuesRepository = issuesRepository;
        this.sonarWebClient = sonarWebClient;
        this.props = props;
        this.objectMapper = objectMapper;
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

    //get matric
    private static final Set<String> SR_KEYS = Set.of(
            "new_security_hotspots_reviewed", "new_security_review_rating"
    );

    private static @Nullable String getStatusByMetric(List<SonarWebhookPayload.Condition> conds, String key) {
        if (conds == null) return null;
        for (var c : conds) {
            if (key.equalsIgnoreCase(c.getMetricKey())) {
                if (c.getStatus() != null && !c.getStatus().isBlank()) return c.getStatus();
                // (optional) คำนวณเองถ้าไม่มี status:
                String cmp = c.getComparator();
                String thr = c.getErrorThreshold();
                String act = c.getActualValue();
                if (cmp!=null && thr!=null && act!=null) {
                    try {
                        double a = Double.parseDouble(act), t = Double.parseDouble(thr);
                        boolean pass = switch (cmp) {
                            case "GT", ">"  -> a >  t;
                            case "GTE", ">="-> a >= t;
                            case "LT", "<"  -> a <  t;
                            case "LTE", "<="-> a <= t;
                            default -> true;
                        };
                        return pass ? "OK" : "ERROR";
                    } catch (NumberFormatException ignore) { }
                }
                return null;
            }
        }
        return null;
    }
    private static @Nullable String getSecurityReviewStatus(List<SonarWebhookPayload.Condition> conds) {
        if (conds == null) return null;
        for (var c : conds) {
            String k = c.getMetricKey();
            if ("new_security_hotspots_reviewed".equalsIgnoreCase(k) ||
                    "new_security_review_rating".equalsIgnoreCase(k)) {
                return c.getStatus();
            }
        }
        return null;
    }

    private JsonNode fetchQualityGateNode(@Nullable String analysisId,
                                          String projectKey,
                                          @Nullable String branch) {
        if (analysisId != null && !analysisId.isBlank()) {
            return sonarWebClient.get()
                    .uri(u -> u.path("/api/qualitygates/project_status")
                            .queryParam("analysisId", analysisId)
                            .build())
                    .retrieve().bodyToMono(JsonNode.class).block();
        }
        // fallback: project + branch (ครอบคลุมเคส analysisId ยังไม่มา)
        return sonarWebClient.get()
                .uri(u -> {
                    var b = u.path("/api/qualitygates/project_status")
                            .queryParam("projectKey", projectKey);
                    if (branch != null && !branch.isBlank()) b.queryParam("branch", branch);
                    return b.build();
                })
                .retrieve().bodyToMono(JsonNode.class).block();
    }

    /* ---------------- Parse ---------------- */
    public @Nullable SonarWebhookPayload parse(byte[] body) {
        try { return objectMapper.readValue(body, SonarWebhookPayload.class); }
        catch (Exception e) { log.warn("parse webhook failed: {}", e.toString()); return null; }
    }

    /* ---------------- Entry (async) ---------------- */
    @Async("webhookExecutor")
    public void processAsync(SonarWebhookPayload p, String projectHeader, String deliveryId) {
        log.info("processAsync on thread={}", Thread.currentThread().getName());
        try { process(p, projectHeader, deliveryId); }
        catch (Exception e) { log.error("webhook process error: {}", e.toString(), e); }
    }

    // --- helpers (วางบนสุดของ service) ---

    private static @Nullable Double parseD(@Nullable String s) {
        if (s == null || s.isBlank()) return null;
        try { return Double.parseDouble(s.trim()); } catch (NumberFormatException e) { return null; }
    }

    /** 1..5 -> "A".."E" (string) */
    private static String ratingToLetterStr(@Nullable Double r) {
        if (r == null) return "N/A";
        int v = (int) Math.round(r);
        return switch (v) {
            case 1 -> "A";
            case 2 -> "B";
            case 3 -> "C";
            case 4 -> "D";
            case 5 -> "E";
            default -> "N/A";
        };
    }

    /** ดึงเลข 1..5 จาก conditions ตาม metric; คืน Double หรือ null */
    private static @Nullable Double ratingFromConds(
            @Nullable List<SonarWebhookPayload.Condition> conds, String metricKey) {
        if (conds == null) return null;
        for (var c : conds) {
            if (metricKey.equalsIgnoreCase(c.getMetricKey())) {
                var raw = (c.getActualValue()!=null) ? c.getActualValue() : c.getValue();
                var d = parseD(raw);
                if (d != null) return d;
            }
        }
        return null;
    }

    /** Security review → เกรดเป็น String: ใช้ rating ก่อน, ไม่มีค่อยใช้ % hotspots */
    private static String secReviewLetter(
            @Nullable List<SonarWebhookPayload.Condition> conds,
            @Nullable Double fallbackRating, @Nullable Double fallbackPct) {

        Double rating = ratingFromConds(conds, "new_security_review_rating");
        if (rating == null) rating = ratingFromConds(conds, "security_review_rating");
        if (rating != null) return ratingToLetterStr(rating);

        Double pct = null;
        if (conds != null) {
            for (var c : conds) {
                String k = c.getMetricKey();
                if ("new_security_hotspots_reviewed".equalsIgnoreCase(k) ||
                        "security_hotspots_reviewed".equalsIgnoreCase(k)) {
                    pct = parseD((c.getActualValue()!=null)?c.getActualValue():c.getValue());
                    break;
                }
            }
        }
        if (pct == null) pct = fallbackPct;
        if (pct != null) {
            if (pct >= 100) return "A";
            if (pct >= 80)  return "B";
            if (pct >= 60)  return "C";
            if (pct >= 40)  return "D";
            return "E";
        }
        if (fallbackRating != null) return ratingToLetterStr(fallbackRating);

        return "N/A";
    }

    private static Double coalesceD(Double... vs){ for (var v:vs) if(v!=null) return v; return null; }
    private static String nvl(String v, String d){ return (v==null || v.isBlank())?d:v; }

    /* ---------------- Main workflow ---------------- */
    private void process(SonarWebhookPayload p, String projectHeader, String deliveryId) throws Exception {
        String projectKey = (p.getProject()!=null && p.getProject().getKey()!=null)
                ? p.getProject().getKey()
                : projectHeader;

        String taskId = p.getTaskId();
        String analysedAt = p.getAnalysedAt();  // <-- ใช้ชื่อ getter ของคุณจริง ๆ

        Instant when = parseSonarTimestamp(analysedAt);

        if (projectKey == null || taskId == null) {
            log.warn("webhook missing projectKey/taskId, delivery={}", deliveryId);
            return;
        }
        final String branch = Optional.ofNullable(p.getBranch())
                .map(b -> b.getName()).orElse(null);

        // 1) task -> analysisId
        String analysisId = fetchAnalysisIdWithRetry(taskId);

        JsonNode qgNode = fetchQualityGateNode(analysisId, projectKey, branch);
        String qgStatus = null;
        List<SonarWebhookPayload.Condition> conditions =
                Optional.ofNullable(p.getQualityGate()).map(SonarWebhookPayload.QualityGate::getConditions).orElse(null);


        if (qgNode != null) {
            qgStatus = qgNode.path("projectStatus").path("status").asText(null);
            var arr = qgNode.path("projectStatus").path("conditions");
            if (arr.isArray() && arr.size() > 0) {
                conditions = new ArrayList<>();
                for (JsonNode n : arr) {
                    var c = new SonarWebhookPayload.Condition();
                    c.setMetricKey(n.path("metricKey").asText(null));
                    c.setComparator(n.path("comparator").asText(null));
                    c.setActualValue(n.path("actualValue").asText(null));
                    c.setErrorThreshold(n.path("errorThreshold").asText(null));
                    c.setStatus(n.path("status").asText(null));
                    conditions.add(c);
                }
            }
        }

        // 2) เตรียมค่า QG + conditions
//        String qgStatus = (p.getQualityGate()!=null ? p.getQualityGate().getStatus() : null);
//        List<SonarWebhookPayload.Condition> conditions =
//                (p.getQualityGate()!=null ? p.getQualityGate().getConditions() : null);
//
//        if (analysisId != null && (qgStatus == null || conditions == null || conditions.isEmpty())) {
//            // fallback ไปดึงจาก API
//            var qgNode = sonarWebClient.get()
//                    .uri(u -> u.path("/api/qualitygates/project_status").queryParam("analysisId", analysisId).build())
//                    .retrieve().bodyToMono(JsonNode.class).block();
//
//            if (qgNode != null) {
//                qgStatus = (qgStatus != null) ? qgStatus
//                        : qgNode.path("projectStatus").path("status").asText(null);
//
//                var arr = qgNode.path("projectStatus").path("conditions");
//                if (arr.isArray()) {
//                    final List<SonarWebhookPayload.Condition> parsed = new ArrayList<>();
//                    arr.forEach(n -> {
//                        var c = new SonarWebhookPayload.Condition();
//                        c.setMetricKey(n.path("metricKey").asText());
//                        c.setStatus(n.path("status").asText());
//                        c.setComparator(n.path("comparator").asText(null));
//                        c.setActualValue(n.path("actualValue").asText(null));
//                        c.setErrorThreshold(n.path("errorThreshold").asText(null));
//                        parsed.add(c);
//                    });
//                    conditions = parsed;   // ← assign ครั้งเดียวหลังจบ lambda
//                }
//            }
//        }

        // 3) map → 4 คอลัมน์
        Double maintainNum = coalesceD(
                ratingFromConds(conditions, "new_maintainability_rating"),
                ratingFromConds(conditions, "sqale_rating")
        );
        Double reliabNum = coalesceD(
                ratingFromConds(conditions, "new_reliability_rating"),
                ratingFromConds(conditions, "reliability_rating")
        );
        Double secNum = coalesceD(
                ratingFromConds(conditions, "new_security_rating"),
                ratingFromConds(conditions, "security_rating")
        );

// 2) ถ้ายังไม่มี → fallback จาก measures ที่คุณมีอยู่แล้ว
        var r = fetchRatingsWithFallback(projectKey, branch);
        if (maintainNum == null) maintainNum = r.mai();
        if (reliabNum   == null) reliabNum   = r.rel();
        if (secNum      == null) secNum      = r.sec();

// 3) แปลงเป็น String A–E
        String maintainLetter = ratingToLetterStr(maintainNum);
        String reliabLetter   = ratingToLetterStr(reliabNum);
        String secLetter      = ratingToLetterStr(secNum);

// 4) Security Review เป็น A–E เช่นกัน (รองรับทั้ง rating และ %)
        String secReviewLetter = secReviewLetter(conditions, r.secReviewRating(), r.hotspotsReviewed());


        // 4) Metrics (ถ้าต้องการ)
        Map<String,Object> metrics = fetchMetrics(projectKey, branch);

        // 5) upsert project
        ProjectsEntity project = projectsRepository.findBySonarProjectKey(projectKey)
                .orElseGet(() -> {
                    var pe = new ProjectsEntity();
                    pe.setSonarProjectKey(projectKey);
                    pe.setName(projectKey);
                    return projectsRepository.save(pe);
                });

        // 6) upsert scan (idempotent)
//        ScansEntity scan = scansRepository.findByAnalysisId(analysisId)
//                .or(() -> scansRepository.findByDeliveryId(deliveryId))
//                .orElseGet(ScansEntity::new);

        Optional<ScansEntity> scanOpt = Optional.empty();
        if (analysisId != null && !analysisId.isBlank()) {
            scanOpt = scansRepository.findByAnalysisId(analysisId);
        }
        if (scanOpt.isEmpty() && deliveryId != null && !deliveryId.isBlank()) {
            scanOpt = scansRepository.findByDeliveryId(deliveryId);
        }
        ScansEntity scan = scanOpt.orElseGet(ScansEntity::new);

        scan.setProject(project);
        scan.setAnalysisId(analysisId);
        scan.setDeliveryId(deliveryId);
        scan.setStatus("SUCCESS");
        scan.setQualityGate(qgStatus != null ? qgStatus.toUpperCase() : "UNKNOWN");
        scan.setMaintainabilityGate(nvl(maintainLetter, "N/A"));
        scan.setReliabilityGate(nvl(reliabLetter, "N/A"));
        scan.setSecurityGate(nvl(secLetter, "N/A"));
        scan.setSecurityReviewGate(nvl(secReviewLetter, "N/A"));
        scan.setMetrics((JsonNode) metrics);
        scan.setStartedAt(LocalDateTime.now());     // หรือ parse จาก p.getAnalysedAt()
        scan.setCompletedAt(LocalDateTime.now());
        scan.setLogFilePath("-(webhook)");

        // เก็บ raw payload (แนะนำ)
        scan.setPayloadJson(objectMapper.valueToTree(p));

        scansRepository.save(scan);

        // 7) (ถ้ามี) import issues ต่อ…
        importIssues(projectKey, scan);

        log.info("Webhook stored: proj={}, analysis={}, QG={}, conds={}", projectKey, analysisId, scan.getQualityGate(), conditions!=null?conditions.size():0);

//        // 2) Quality Gate ณ analysis นั้น (ถ้ามีใน payload เอาค่านั้นก่อน)
//        String qg = (p.getQualityGate()!=null && p.getQualityGate().getStatus()!=null)
//                ? p.getQualityGate().getStatus().toUpperCase(Locale.ROOT)
//                : fetchQualityGate(analysisId);
//
//        // 3) Metrics ล่าสุดของโปรเจ็กต์ (ถ้า payload บอก branch ก็แนบไป)
//        Map<String,Object> metrics = fetchMetrics(projectKey);
//
//        // 4) Upsert Project
//        ProjectsEntity project = projectsRepository.findBySonarProjectKey(projectKey)
//                .orElseGet(() -> {
//                    ProjectsEntity pe = new ProjectsEntity();
//                    pe.setSonarProjectKey(projectKey);
//                    pe.setName(projectKey);
//                    return projectsRepository.save(pe);
//                });
//
//        // 5) สร้างแถว Scan ใหม่ (หรือจะทำ idempotent ด้วย deliveryId/analysisId ก็ได้)
//        ScansEntity scan = new ScansEntity();
//        scan.setProject(project);
//        scan.setStatus("SUCCESS");
//        scan.setQualityGate(qg);
//        scan.setMetrics(metrics);
//        scan.setStartedAt(LocalDateTime.ofInstant(when, ZoneOffset.UTC));
//        scan.setCompletedAt(LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC));
//        scan.setLogFilePath("- (webhook)");
//        scansRepository.save(scan);
//
//        // 6) ดึง Issues ไล่หน้าแล้ว upsert ลงตาราง issues ผูกกับ scan นี้
//        importIssues(projectKey, scan);
//
//        log.info("Webhook stored: project={}, qg={}, delivery={}", projectKey, qg, deliveryId);
    }

    private static String coalesce(String... vs) {
        for (String v : vs) if (v != null && !v.isBlank()) return v;
        return null;
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

    private @Nullable String fetchAnalysisIdWithRetry(String taskId) {
        if (taskId == null) return null;
        int maxTries = 9;          // ~6s
        long delayMs = 1000;
        for (int i = 0; i < maxTries; i++) {
            String aid = fetchAnalysisId(taskId);
            if (aid != null && !aid.isBlank()) return aid;
            try { Thread.sleep(delayMs); } catch (InterruptedException ignored) {}
        }
        return null;
    }

    /* ---------------- Sonar calls ---------------- */

    private Ratings fetchRatingsWithFallback(String projectKey, @Nullable String branch) {
        Ratings r = fetchRatings(projectKey, branch, true);
        if (r.rel()==null || r.sec()==null || r.mai()==null || (r.hotspotsReviewed()==null && r.secReviewRating()==null)) {
            Ratings o = fetchRatings(projectKey, branch, false);
            return new Ratings(
                    r.rel()!=null ? r.rel() : o.rel(),
                    r.sec()!=null ? r.sec() : o.sec(),
                    r.mai()!=null ? r.mai() : o.mai(),
                    r.hotspotsReviewed()!=null ? r.hotspotsReviewed() : o.hotspotsReviewed(),
                    r.secReviewRating()!=null ? r.secReviewRating() : o.secReviewRating()
            );
        }
        return r;
    }

    /** new=true => new_* ; new=false => overall */
    private Ratings fetchRatings(String projectKey, @Nullable String branch, boolean isNew) {
        String metrics = isNew
                ? "new_reliability_rating,new_security_rating,new_maintainability_rating,new_security_hotspots_reviewed,new_security_review_rating"
                : "reliability_rating,security_rating,sqale_rating,security_hotspots_reviewed,security_review_rating";
        JsonNode root = sonarWebClient.get()
                .uri(u -> {
                    var b = u.path("/api/measures/component")
                            .queryParam("component", projectKey)
                            .queryParam("metricKeys", metrics);
                    if (branch != null && !branch.isBlank()) b.queryParam("branch", branch);
                    return b.build();
                })
                .retrieve().bodyToMono(JsonNode.class).block();

        Double rel=null, sec=null, mai=null, hot=null, srr=null;
        if (root != null) {
            for (JsonNode m : root.path("component").path("measures")) {
                String k = m.path("metric").asText();
                Double v = m.has("value") ? m.get("value").asDouble() : null;
                switch (k) {
                    case "new_reliability_rating", "reliability_rating" -> rel = v;
                    case "new_security_rating", "security_rating" -> sec = v;
                    case "new_maintainability_rating", "sqale_rating" -> mai = v;
                    case "new_security_hotspots_reviewed", "security_hotspots_reviewed" -> hot = v;
                    case "new_security_review_rating", "security_review_rating" -> srr = v;
                }
            }
        }
        return new Ratings(rel, sec, mai, hot, srr);
    }


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

    private Map<String,Object> fetchMetrics(String projectKey, @Nullable String branch) {
        Map<String,Object> result = new LinkedHashMap<>();
        try {
            JsonNode root = sonarWebClient.get()
                    .uri(u -> {
                        var b = u.path("/api/measures/component")
                                .queryParam("component", projectKey)
                                .queryParam("metricKeys", sanitizeMetrics(props.getMetricsCsv(), metricsCsv));
                        if (branch != null && !branch.isBlank()) b.queryParam("branch", branch);
                        return b.build();
                    })
                    .retrieve().bodyToMono(JsonNode.class).block();

            JsonNode measures = (root==null) ? null : root.path("component").path("measures");
            if (measures != null && measures.isArray()) {
                for (JsonNode m : measures) {
                    String k = m.path("metric").asText();
                    String v = m.hasNonNull("value") ? m.get("value").asText() : "N/A";
                    result.put(k, v);
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
        IssuesEntity entity = (IssuesEntity) issuesRepository
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

    private static final String FIXED_BRANCH = "main";

    /** ดึงเรตติ้ง/เปอร์เซ็นต์สำหรับทำ 4 gate */
    private record Ratings(Double rel, Double sec, Double mai, Double hotspotsReviewed, Double secReviewRating) {}

    private Ratings fetchRatingsForMain(String projectKey) {
        JsonNode root = sonarWebClient.get()
                .uri(u -> u.path("/api/measures/component")
                        .queryParam("component", projectKey)
                        .queryParam("branch", FIXED_BRANCH)
                        .queryParam("metricKeys",
                                // new_* ถ้าจะใช้ “บน New Code”; หรือใช้ overall ก็เปลี่ยนชื่อ metric ได้
                                "new_reliability_rating,new_security_rating,new_maintainability_rating," +
                                        "new_security_hotspots_reviewed,new_security_review_rating")
                        .build())
                .retrieve().bodyToMono(JsonNode.class).block();

        Double rel=null, sec=null, mai=null, hot=null, srr=null;
        for (JsonNode m : root.path("component").path("measures")) {
            String k = m.path("metric").asText();
            Double v = m.has("value") ? m.get("value").asDouble() : null;
            switch (k) {
                case "new_reliability_rating"     -> rel = v;
                case "new_security_rating"        -> sec = v;
                case "new_maintainability_rating" -> mai = v;
                case "new_security_hotspots_reviewed" -> hot = v;
                case "new_security_review_rating" -> srr = v;
            }
        }
        return new Ratings(rel, sec, mai, hot, srr);
    }

    /** (ถ้าจำเป็น) ดึงสรุป Quality Gate พร้อม conditions ด้วย branch=main */
    private JsonNode fetchQualityGateForMain(String projectKey) {
        return sonarWebClient.get()
                .uri(u -> u.path("/api/qualitygates/project_status")
                        .queryParam("projectKey", projectKey)
                        .queryParam("branch", FIXED_BRANCH)
                        .build())
                .retrieve().bodyToMono(JsonNode.class).block();
    }

    private static String gateFromRatingAB(@Nullable Double rating1to5) {
        // 1=A, 2=B … 5=E
        if (rating1to5 == null) return "N/A";
        return rating1to5 <= 2.0 ? "OK" : "ERROR"; // A/B ผ่าน
    }

    private static String gateFromHotspots(@Nullable Double percentReviewed) {
        if (percentReviewed == null) return "N/A";
        // นโยบายทั่วไป: รีวิวครบ 100% ถึงจะผ่าน (ปรับได้)
        return percentReviewed >= 100.0 ? "OK" : "ERROR";
    }
}


