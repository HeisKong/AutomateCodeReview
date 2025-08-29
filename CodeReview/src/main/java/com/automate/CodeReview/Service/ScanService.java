package com.automate.CodeReview.Service;


import com.automate.CodeReview.Models.ScanLogModel;
import com.automate.CodeReview.Models.ScanModel;
import com.automate.CodeReview.Models.ScanRequest;
import com.automate.CodeReview.entity.ProjectsEntity;
import com.automate.CodeReview.entity.ScansEntity;
import com.automate.CodeReview.repository.ProjectsRepository;
import com.automate.CodeReview.repository.ScansRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

@Slf4j
@Service
public class ScanService {

    private final ScansRepository scanRepository;
    private final ProjectsRepository projectRepository;

    public ScanService(ScansRepository scanRepository, ProjectsRepository projectRepository) {
        this.scanRepository = scanRepository;
        this.projectRepository = projectRepository;
    }

    // ส่วนของ startScan
    @Value("${sonar.host-url}")
    private String sonarHostUrlCfg;

    @Value("${sonar.useHostInternalOnWindows:true}")
    private boolean useHostInternalOnWindows;

    @Value("${sonar.metrics:bugs,vulnerabilities,code_smells,coverage,duplicated_lines_density}")
    private String metricsKeys;

    @Value("${sonar.scanner-image:sonarsource/sonar-scanner-cli}")
    private String scannerImage;


    // retry config
    @Value("${sonar.metrics.retries:3}")
    private int metricsRetries;

    @Value("${sonar.metrics.delay-ms:2000}")
    private long metricsDelayMs;

    @Value("${sonar.metrics.max-delay-ms:15000}")
    private long metricsMaxDelayMs;

    private static final boolean IS_WINDOWS =
            System.getProperty("os.name").toLowerCase().contains("win");

    private String sonarHostUrl() {
        if (IS_WINDOWS && useHostInternalOnWindows) return "http://host.docker.internal:9000";
        return sonarHostUrlCfg;
    }



    @Transactional
    public ScanModel startScan(ScanRequest req) {
        Objects.requireNonNull(req.getRepoUrl(), "repoUrl is required");
        Objects.requireNonNull(req.getProjectKey(), "projectKey is required");
        Objects.requireNonNull(req.getToken(), "token is required");

        ScanModel res = new ScanModel();
        res.setScanId(UUID.randomUUID());

        StringBuilder logs = new StringBuilder();

        res.setRepositoryUrl(req.getRepoUrl());
        res.setSonarProjectKey(req.getProjectKey());
        res.setBranchName(req.getBranchName());
        res.setStartedAt(LocalDateTime.now());
        res.setLogFilePath(writeLogsToFile(res.getScanId(), logs));

        Path repoRoot = null;

        
        

        try {
            // 1) clone repo
            repoRoot = Files.createTempDirectory("scan-");
            List<String> gitCmd = req.getBranchName() != null && !req.getBranchName().isBlank()
                    ? List.of("git","clone","--depth","1","--branch",req.getBranchName(),"--single-branch",req.getRepoUrl(),repoRoot.toString())
                    : List.of("git","clone","--depth","1",req.getRepoUrl(),repoRoot.toString());
            int gitExit = runAndCapture(gitCmd, logs, null);
            if (gitExit != 0) return fail(res, gitExit, "git clone failed\n"+logs);

            // 2) detect project type
            boolean isAngular = isAngularProject(repoRoot);
            Path pomDir = hasPom(repoRoot);
            Path gradleDir = hasGradle(repoRoot);

            int exit;
            if (isAngular) {
                exit = runAndCapture(dockerNodeInstall(repoRoot), logs, null);
                if (exit != 0) return fail(res, exit, "npm install failed\n"+logs);
                runAndCapture(dockerNodeCoverage(repoRoot), logs, null); // optional
                exit = runAndCapture(dockerSonarScanAngular(req, repoRoot), logs, repoRoot.toFile());
            } else if (pomDir != null) {
                exit = runAndCapture(dockerMavenBuild(pomDir), logs, null);
                if (exit != 0) return fail(res, exit, "maven build failed\n"+logs);
                exit = runAndCapture(dockerSonarScanMaven(req, repoRoot, pomDir), logs, repoRoot.toFile());
            } else if (gradleDir != null) {
                exit = runAndCapture(dockerGradleBuild(gradleDir), logs, null);
                if (exit != 0) return fail(res, exit, "gradle build failed\n"+logs);
                exit = runAndCapture(dockerSonarScanGradle(req, repoRoot, gradleDir), logs, repoRoot.toFile());
            } else {
                return fail(res, 1, "Unknown project type: not Angular/Maven/Gradle");
            }

            res.setExitCode(exit);
            res.setOutput(logs.toString());
            res.setStatus(exit == 0 ? "SUCCESS" : "FAIL");

            // 3) fetch QG + metrics if success
            if (res.getExitCode() == 0) {
                Optional<ReportTask> rtOpt = readReportTask(repoRoot);

                // 1) เตรียม candidate host สำหรับ “ฝั่งแอป”
                String fromConfig = sonarHostUrl(); // เช่น http://host.docker.internal:9000 (Windows)
                String fromReport = rtOpt.map(rt -> rt.serverUrl).orElse(null);

                if (fromReport != null && fromReport.contains("://sonarqube")) {
                    fromReport = null;
                }

                // กรณี report-task เขียนเป็นชื่อ service ใน Docker (เช่น http://sonarqube:9000)
                // มักจะ "เอื้อมไม่ถึง" จาก Host → เราจะแค่ลอง แต่ถ้าไม่ถึงจะ fallback เอง
                String reachableHost = pickReachableSonarHost(
                        req.getToken(),
                        fromConfig,                                 // <— ใช้ค่าคอนฟิกก่อน (localhost)
                        fromReport,
                        "http://localhost:9000",
                        "http://host.docker.internal:9000"         // <— เผื่อไว้ (คุณทดสอบว่าเรียกได้จาก container)
                );
                if (reachableHost == null) {
                    res.setQualityGate("UNKNOWN");
                    res.setMetrics(Map.of(
                            "error","fetch metrics failed",
                            "message","no reachable Sonar host (timeout)"
                    ));
                } else {
                    // 2) รอ CE task เสร็จ (ถ้ามี ceTaskId) ด้วย host ที่เข้าถึงได้จริง
                    if (rtOpt.isPresent() && rtOpt.get().ceTaskId != null) {
                        boolean done = waitForCeTaskDone(reachableHost, req.getToken(), rtOpt.get().ceTaskId, 90_000);
                        if (done) {
                            res.setQualityGate(fetchQualityGateByAnalysis(reachableHost, req.getToken(), rtOpt.get().ceTaskId));
                        } else {
                            // fallback: ดึง QG แบบ projectKey แทน
                            res.setQualityGate(fetchQualityGate(reachableHost, req.getToken(), req.getProjectKey(), null));
                        }
                    } else {
                        // ไม่มี ceTaskId → ใช้ projectKey
                        res.setQualityGate(fetchQualityGate(reachableHost, req.getToken(), req.getProjectKey(), null));
                    }

                    // 3) ดึง metrics ด้วย retry + host ที่เลือกไว้
                    Map<String,Object> metrics = fetchMetricsWithRetry(
                            reachableHost, req.getToken(), req.getProjectKey(),
                            null, metricsKeys, metricsRetries, metricsDelayMs, metricsMaxDelayMs
                    );
                    res.setMetrics(metrics);
                }
            }

        } catch (Exception e) {
            res.setExitCode(-1);
            res.setStatus("ERROR");
            res.setOutput(e.getMessage());
        } finally {
            res.setCompletedAt(LocalDateTime.now());
            // เขียนไฟล์ log หลังจบ (เนื้อหาเต็มแล้ว)  // <--
            String logPath = writeLogsToFile(res.getScanId(), logs);
            res.setLogFilePath(logPath);
            if (repoRoot != null) deleteQuietly(repoRoot);
        }



        // บันทึกลง DB
        ScansEntity entity = new ScansEntity();

        ProjectsEntity project = projectRepository.findBySonarProjectKey(req.getProjectKey())
                .orElseGet(() -> {
                    ProjectsEntity p = new ProjectsEntity();
                    p.setSonarProjectKey(req.getProjectKey());
                    p.setName(req.getProjectKey());
                    p.setRepositoryUrl(req.getRepoUrl());
                    return projectRepository.save(p);
                });

        entity.setProject(project);
        entity.setStatus(res.getStatus());
        entity.setMetrics(res.getMetrics());
        entity.setLogFilePath(res.getLogFilePath());
        entity.setQualityGate(res.getQualityGate());
        entity.setStartedAt(res.getStartedAt());
        entity.setCompletedAt(res.getCompletedAt());
        entity.setLogFilePath(res.getLogFilePath() == null ? "-" : res.getLogFilePath());
        try {
            scanRepository.save(entity);
        } catch (Exception e) {
            e.printStackTrace(); // จะบอกบรรทัดพังชัดเจนใน console
            throw e; // หรือ return ResponseEntity.error(...) แทน
        }

        return res;
    }
    static class ReportTask {
        String ceTaskId;
        String projectKey;
        String serverUrl;
    }

    private Optional<ReportTask> readReportTask(Path repoRoot) {
        if (repoRoot == null) return Optional.empty();
        Path f = repoRoot.resolve(".scannerwork").resolve("report-task.txt");
        if (!Files.exists(f)) return Optional.empty();

        try (Reader reader = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
            Properties props = new Properties();
            props.load(reader);

            ReportTask rt = new ReportTask();
            rt.ceTaskId    = props.getProperty("ceTaskId");
            rt.projectKey  = props.getProperty("projectKey");
            rt.serverUrl   = props.getProperty("serverUrl");

            return Optional.of(rt);
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private Map<String,Object> parseJsonToMap(String json) {
        try {
            return new ObjectMapper().readValue(json, new TypeReference<Map<String,Object>>(){});
        } catch (Exception e) {
            return Map.of("error","parse failed","raw", json);
        }
    }


    private String writeLogsToFile(UUID scanId, CharSequence logs) {
        try {
            Path dir = Paths.get(System.getProperty("java.io.tmpdir"), "scan-logs");
            Files.createDirectories(dir);
            Path f = dir.resolve("scan-" + scanId + ".log");
            Files.writeString(f, logs, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return f.toAbsolutePath().toString();
        } catch (IOException e) {
            return null;
        }
    }


    /* ===================== Project type detection ===================== */

    private boolean isAngularProject(Path root) {
        return Files.exists(root.resolve("package.json")) &&
                Files.exists(root.resolve("angular.json"));
    }
    private Path hasPom(Path root) throws IOException {
        try (var s = Files.walk(root, 4)) {
            return s.filter(p -> p.getFileName().toString().equalsIgnoreCase("pom.xml"))
                    .map(Path::getParent).findFirst().orElse(null);
        }
    }
    private Path hasGradle(Path root) throws IOException {
        try (var s = Files.walk(root, 4)) {
            return s.filter(p -> {
                String n = p.getFileName().toString().toLowerCase();
                return n.equals("build.gradle") || n.equals("build.gradle.kts");
            }).map(Path::getParent).findFirst().orElse(null);
        }
    }

    /* ===================== Docker commands per stack ===================== */

    /* ---------- Angular ---------- */
    private List<String> dockerNodeInstall(Path repoRoot) {
        List<String> cmd = new ArrayList<>();
        if (IS_WINDOWS) { cmd.add("cmd"); cmd.add("/c"); }
        cmd.addAll(List.of(
                "docker","run","--rm",
                "-v", repoRoot.toString()+":/usr/src",
                "-w", "/usr/src",
                "node:18",
                "bash","-lc","npm ci || npm install"
        ));
        return cmd;
    }
    private List<String> dockerNodeCoverage(Path repoRoot) {
        // พยายามสร้าง lcov ถ้าโปรเจ็กต์ตั้งไว้ (ไม่บังคับผ่าน)
        List<String> cmd = new ArrayList<>();
        if (IS_WINDOWS) { cmd.add("cmd"); cmd.add("/c"); }
        cmd.addAll(List.of(
                "docker","run","--rm",
                "-v", repoRoot.toString()+":/usr/src",
                "-w", "/usr/src",
                "node:18",
                "bash","-lc","npm run test -- --watch=false --code-coverage || true"
        ));
        return cmd;
    }
    private List<String> dockerSonarScanAngular(ScanRequest req, Path repoRoot) {
        // ใช้พารามิเตอร์ตาม FR: sources/exclusions/tests/lcov
        List<String> cmd = new ArrayList<>();
        if (IS_WINDOWS) { cmd.add("cmd"); cmd.add("/c"); }
        cmd.addAll(List.of(
                "docker","run","--rm",
                "-e","SONAR_HOST_URL=http://host.docker.internal:9000",
                "-e","SONAR_TOKEN="+req.getToken(),
                "-v", repoRoot.toString()+":/usr/src",
                scannerImage,
                "-Dsonar.projectKey="+req.getProjectKey(),
                "-Dsonar.sources=src",
                "-Dsonar.exclusions=**/node_modules/**,**/*.spec.ts",
                "-Dsonar.tests=src",
                "-Dsonar.test.inclusions=**/*.spec.ts",
                "-Dsonar.typescript.lcov.reportPaths=coverage/lcov.info",
                "-Dsonar.sourceEncoding=UTF-8"
        ));
        return cmd;
    }

    /* ---------- Maven (Spring Boot) ---------- */
    private List<String> dockerMavenBuild(Path pomDir) {
        Path repoRoot = pomDir.getParent();
        String rel = repoRoot.relativize(pomDir).toString().replace('\\','/');  // เปลี่ยนตรงนี้
        List<String> cmd = new ArrayList<>();
        if (IS_WINDOWS) { cmd.add("cmd"); cmd.add("/c"); }
        cmd.addAll(List.of(
                "docker","run","--rm",
                "-v", repoRoot.toString()+":/usr/src",
                "-w","/usr/src/"+ rel,                       // ใช้ rel
                "maven:3-eclipse-temurin-21",
                "mvn","-B","-DskipTests","package"
        ));
        return cmd;
    }
    private List<String> dockerSonarScanMaven(ScanRequest req, Path repoRoot, Path pomDir) {
        String rel = repoRoot.relativize(pomDir).toString().replace('\\','/');
        String binaries = rel.isBlank() ? "target/classes" : rel + "/target/classes";
        List<String> cmd = new ArrayList<>();
        if (IS_WINDOWS) { cmd.add("cmd"); cmd.add("/c"); }
        cmd.addAll(List.of(
                "docker","run","--rm",
                "-e","SONAR_HOST_URL=http://host.docker.internal:9000",
                "-e","SONAR_TOKEN="+req.getToken(),
                "-v", repoRoot.toString()+":/usr/src",
                scannerImage,
                "-Dsonar.projectKey="+req.getProjectKey(),
                "-Dsonar.sources=.",
                "-Dsonar.java.binaries="+binaries,
                "-Dsonar.sourceEncoding=UTF-8"
        ));
        return cmd;
    }

    /* ---------- Gradle ---------- */
    private List<String> dockerGradleBuild(Path gradleDir) {
        Path repoRoot = gradleDir.getParent();
        String rel = repoRoot.relativize(gradleDir).toString().replace('\\','/'); // เปลี่ยนตรงนี้
        List<String> cmd = new ArrayList<>();
        if (IS_WINDOWS) { cmd.add("cmd"); cmd.add("/c"); }
        cmd.addAll(List.of(
                "docker","run","--rm",
                "-v", repoRoot.toString()+":/usr/src",
                "-w","/usr/src/"+ rel,                       // ใช้ rel
                "gradle:8-jdk21",
                "gradle","build","-x","test"
        ));
        return cmd;
    }
    private List<String> dockerSonarScanGradle(ScanRequest req, Path repoRoot, Path gradleDir) {
        String rel = repoRoot.relativize(gradleDir).toString().replace('\\','/');
        String binaries = rel.isBlank() ? "build/classes/java/main" : rel + "/build/classes/java/main";
        List<String> cmd = new ArrayList<>();
        if (IS_WINDOWS) { cmd.add("cmd"); cmd.add("/c"); }
        cmd.addAll(List.of(
                "docker","run","--rm",
                "-e","SONAR_HOST_URL=http://host.docker.internal:9000",
                "-e","SONAR_TOKEN="+req.getToken(),
                "-v", repoRoot.toString()+":/usr/src",
                scannerImage,
                "-Dsonar.projectKey="+req.getProjectKey(),
                "-Dsonar.sources=.",
                "-Dsonar.java.binaries="+binaries,
                "-Dsonar.sourceEncoding=UTF-8"
        ));
        return cmd;
    }

    /* ===================== Shared helpers ===================== */

    private int runAndCapture(List<String> cmd, StringBuilder logs, File workDir) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        if (workDir != null) pb.directory(workDir);
        Process p = pb.start();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line; while ((line = br.readLine()) != null) logs.append(line).append("\n");
        }
        return p.waitFor();
    }

    private ScanModel fail(ScanModel res, int exit, String message) {
        res.setExitCode(exit);
        res.setStatus("FAIL");
        res.setOutput(message);
        res.setCompletedAt(LocalDateTime.now());
        return res;
    }

    private String fetchQualityGate(String host, String token, String projectKey, String branch) {
        try {
            String url = host + "/api/qualitygates/project_status?projectKey=" +
                    URLEncoder.encode(projectKey, StandardCharsets.UTF_8);
            String json = httpGetWithToken(url, token);
            int idx = json.indexOf("\"status\":\"");
            if (idx > -1) { int s = idx + 10; int e = json.indexOf("\"", s); return json.substring(s, e); }
        } catch (Exception ignore) {}
        return "UNKNOWN";
    }

    private String fetchQualityGateByAnalysis(String host, String token, String ceTaskId) {
        try {
            // จาก ceTaskId -> analysisId
            String ceUrl = host + "/api/ce/task?id=" + URLEncoder.encode(ceTaskId, StandardCharsets.UTF_8);
            String ceJson = httpGetWithToken(ceUrl, token);
            int ai = ceJson.indexOf("\"analysisId\":\"");
            if (ai > -1) {
                int s = ai + 14, e = ceJson.indexOf("\"", s);
                String analysisId = ceJson.substring(s, e);
                String qgUrl = host + "/api/qualitygates/project_status?analysisId=" +
                        URLEncoder.encode(analysisId, StandardCharsets.UTF_8);
                String qgJson = httpGetWithToken(qgUrl, token);
                int idx = qgJson.indexOf("\"status\":\"");
                if (idx > -1) { int ss = idx + 10; int ee = qgJson.indexOf("\"", ss); return qgJson.substring(ss, ee); }
            }
        } catch (Exception ignore) {}
        return "UNKNOWN";
    }
    private Map<String,Object> fetchMetricsWithRetry(
            String host, String token, String projectKey, String branch, String metricsCsv,
            int retries, long delayMs, long maxDelayMs
    ) {
        Map<String,Object> lastErr = null;
        long delay = delayMs;
        for (int i = 1; i <= retries; i++) {
            Map<String,Object> m = fetchMetricsRaw(host, token, projectKey, branch, metricsCsv); // <--
            if (m != null && !m.containsKey("error")) {
                if (i > 1) {
                    log.info("fetchMetrics success at attempt {}/{}", i, retries);
                }
                return m;
            }
            lastErr = (m == null) ? Map.of("error", "null response") : m;
            log.warn("fetchMetrics attempt {}/{} failed: {}", i, retries, lastErr);

            if (i < retries) {
                try { Thread.sleep(delay); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                delay = Math.min(delay * 2, maxDelayMs);  // exponential backoff
            }
        }
        Map<String,Object> err = new LinkedHashMap<>();
        err.put("error", "fetch metrics failed after retry");
        if (lastErr != null && lastErr.get("message") != null) {
            err.put("message", lastErr.get("message"));
        }
        return err;
    }

    private Map<String,Object> fetchMetricsRaw(
            String host, String token, String projectKey, String branch, String metricsCsv
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            String url = host + "/api/measures/component?component=" +
                    URLEncoder.encode(projectKey, StandardCharsets.UTF_8) +
                    "&metricKeys=" + URLEncoder.encode(metricsCsv, StandardCharsets.UTF_8);
            if (branch != null && !branch.isBlank()) {
                url += "&branch=" + URLEncoder.encode(branch, StandardCharsets.UTF_8);
            }

            String json = httpGetWithToken(url, token);
            ObjectMapper om = new ObjectMapper();
            JsonNode root = om.readTree(json);
            JsonNode measures = root.path("component").path("measures");

            if (measures.isArray()) {
                for (JsonNode m : measures) {
                    String metric = m.path("metric").asText();
                    if (metric == null || metric.isBlank()) continue;
                    JsonNode v = m.get("value");
                    result.put(metric, (v != null && !v.isNull()) ? v.asText() : "N/A");
                }
            }
            return result;
        } catch (Exception e) {
            return Map.of(
                    "error", "fetch metrics failed",
                    "message", e.getMessage()
            );
        }
    }

    private boolean pingSonar(String host, String token) {
        try {
            // ใช้ endpoint public ที่ไม่ต้องเป็น admin
            String url = host + "/api/server/version";
            String json = httpGetWithToken(url, token); // จะได้เวอร์ชันเช่น "10.6.0.92116"
            return json != null && !json.isBlank();
        } catch (Exception e) {
            log.warn("Ping failed {}: {}", host, e.toString());
            return false;
        }
    }

    /** เลือก host ที่ “เอื้อมถึงจริง” สำหรับฝั่งแอป */
    private String pickReachableSonarHost(String token, String... candidates) {
        for (String h : candidates) {
            if (h == null || h.isBlank()) continue;
            if (pingSonar(h, token)) {
                log.info("Use reachable Sonar host: {}", h);
                return h;
            } else {
                log.warn("Sonar host not reachable: {}", h);
            }
        }
        return null;
    }

    private String httpGetWithToken(String url, String token) throws IOException {
        String basic = Base64.getEncoder().encodeToString((token + ":").getBytes(StandardCharsets.UTF_8));
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setRequestMethod("GET");
        con.setRequestProperty("Authorization", "Basic " + basic);
        con.setConnectTimeout(25000);
        con.setReadTimeout(60000);

        int code = con.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? con.getInputStream() : con.getErrorStream();
        String body = (is != null) ? new String(is.readAllBytes(), StandardCharsets.UTF_8) : "";

        if (code < 200 || code >= 300) {
            throw new IOException("HTTP " + code + " " + url + " :: " + body);
        }
        return body;
    }

    private void deleteQuietly(Path path) {
        try {
            Files.walk(path).sorted(Comparator.reverseOrder())
                    .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
        } catch (IOException ignored) {}
    }

    private boolean waitForCeTaskDone(String host, String token, String ceTaskId, long timeoutMs) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeoutMs) {
            try {
                String url = host + "/api/ce/task?id=" + URLEncoder.encode(ceTaskId, StandardCharsets.UTF_8);
                String json = httpGetWithToken(url, token);
                // หา "status":"SUCCESS" หรือ "FAILED"/"CANCELED"
                int st = json.indexOf("\"status\":\"");
                if (st > -1) {
                    int s = st + 10, e = json.indexOf("\"", s);
                    String status = json.substring(s, e);
                    if ("SUCCESS".equalsIgnoreCase(status)) return true;
                    if ("FAILED".equalsIgnoreCase(status) || "CANCELED".equalsIgnoreCase(status)) return false;
                }
            } catch (Exception ignore) {}

            try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
        }
        return false; // Timeout
    }



    //ส่วนของ startScan

    public List<ScanModel> getAllScan(){
        List<ScanModel> scansModel = new ArrayList<>();
        List<ScansEntity> scansEntities = scanRepository.findAll();


        for(ScansEntity scanEntity : scansEntities){
            ScanModel model = new ScanModel();
            model.setScanId(scanEntity.getScanId());
            model.setProjectId(scanEntity.getProject().getProjectId());
            model.setStatus(scanEntity.getStatus());
            model.setStartedAt(scanEntity.getStartedAt());
            model.setCompletedAt(scanEntity.getCompletedAt());
            model.setQualityGate(String.valueOf(scanEntity.getQualityGate()));
            model.setLogFilePath(String.valueOf(scanEntity.getLogFilePath()));
            scansModel.add(model);
        }
        return scansModel;
    }

    public ScanModel GetByIdScan(UUID id){
        //จะใช้อันนนี้ก้ได้นะเเต่ถ้าทำใน repo มันใช้ jdbc ดึงได้เลย
        return null;
    }

    public ScanModel getLogScan(UUID id){
        return null;
    }

    public ScanModel cancelScan(UUID scanId){
        ScansEntity scan = scanRepository.findById(scanId)
                .orElseThrow(() -> new RuntimeException("Scan not found"));

        if (scan.getStatus().equals("RUNNING")) {
            scan.setStatus("CANCELLED");
            scan.setCompletedAt(LocalDateTime.now());
            scanRepository.save(scan);
        }
        return toModel(scan);
    }

    public ScanLogModel getScanLogById(UUID id) {
        return null;
    }

    private ScanModel toModel(ScansEntity entity) {
        ScanModel model = new ScanModel();
        model.setScanId(entity.getScanId());
        model.setProjectId(entity.getProject().getProjectId());
        model.setStatus(entity.getStatus());
        model.setQualityGate(entity.getQualityGate());
        model.setStartedAt(entity.getStartedAt());
        model.setCompletedAt(entity.getCompletedAt());
        return model;
    }
}
