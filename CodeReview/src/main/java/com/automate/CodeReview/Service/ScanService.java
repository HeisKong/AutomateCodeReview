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
import jakarta.annotation.Nullable;
import jakarta.annotation.Resource;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
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
import com.automate.CodeReview.exception.ScanNotFoundException;
import com.automate.CodeReview.exception.ScanCancelConflictException;


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

    // จำนวน retry ในการดึง metrics
    @Value("${sonar.metrics.retries:3}")
    private int metricsRetries;

    @Value("${sonar.metrics.delay-ms:2000}")
    private long metricsDelayMs;

    @Value("${sonar.metrics.max-delay-ms:15000}")
    private long metricsMaxDelayMs;

    // Executables (ถ้าอยู่ใน PATH ใช้ค่า default ได้เลย)
    @Value("${sonar.scanner-exec:sonar-scanner}")
    private String scannerExec; // เช่น C:\\sonar\\bin\\sonar-scanner.bat บน Windows

    @Value("${node.exec:node}")
    private String nodeExec;    // เผื่ออยาก fix path (จริง ๆ ไม่ได้ใช้ตรง ๆ ในสคริปต์)
    @Value("${npm.exec:npm}")
    private String npmExec;

    @Value("${maven.exec:mvn}")
    private String mavenExec;

    @Value("${gradle.exec:gradle}")
    private String gradleExec;

    private static final boolean IS_WINDOWS =
            System.getProperty("os.name").toLowerCase().contains("win");

    private String sonarHostUrl() {
        if (IS_WINDOWS && useHostInternalOnWindows) return "http://host.docker.internal:9000";
        return sonarHostUrlCfg;
    }

    /* ===================== API หลัก ===================== */

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
            // 1) clone repo (host)
            repoRoot = Files.createTempDirectory("scan-");
            List<String> gitCmd = (req.getBranchName() != null && !req.getBranchName().isBlank())
                    ? List.of("git","clone","--depth","1","--branch",req.getBranchName(),"--single-branch",
                    req.getRepoUrl(),repoRoot.toString())
                    : List.of("git","clone","--depth","1",req.getRepoUrl(),repoRoot.toString());
            int gitExit = runAndCapture(gitCmd, logs, null, null);
            if (gitExit != 0) return fail(res, gitExit, "git clone failed\n"+logs);

            // 2) detect project type
            boolean isAngular = isAngularProject(repoRoot);
            Path pomDir = hasPom(repoRoot);
            Path gradleDir = hasGradle(repoRoot);

            // 3) prepare Sonar env (อย่าใส่ token ใน command line)
            Map<String,String> sonarEnv = new HashMap<>();
            sonarEnv.put("SONAR_HOST_URL", sonarHostUrl());
            sonarEnv.put("SONAR_TOKEN", req.getToken());

            int exit;
            if (isAngular) {
                // npm install
                exit = runAndCapture(hostNodeInstall(repoRoot), logs, repoRoot.toFile(), null);
                if (exit != 0) return fail(res, exit, "npm install failed\n"+logs);

                // optional coverage
                runAndCapture(hostNodeCoverage(repoRoot), logs, repoRoot.toFile(), null);

                // sonar-scanner CLI
                exit = runAndCapture(hostSonarScanAngular(req, repoRoot), logs, repoRoot.toFile(), sonarEnv);

            } else if (pomDir != null) {
                // Maven build
                exit = runAndCapture(hostMavenBuild(pomDir), logs, pomDir.toFile(), null);
                if (exit != 0) return fail(res, exit, "maven build failed\n"+logs);

                // Maven Sonar plugin (ไม่ใส่ token ใน cmd; ส่งผ่าน ENV)
                logs.append("[scan] Using Maven plugin: mvn verify sonar:sonar\n");
                exit = runAndCapture(
                        hostMavenSonarGoal(req, pomDir),
                        logs,
                        pomDir.toFile(),
                        sonarEnv
                );

            } else if (gradleDir != null) {
                // Gradle build
                exit = runAndCapture(hostGradleBuild(gradleDir), logs, gradleDir.toFile(), null);
                if (exit != 0) return fail(res, exit, "gradle build failed\n"+logs);

                // sonar-scanner CLI
                exit = runAndCapture(hostSonarScanGradle(req, repoRoot, gradleDir), logs, repoRoot.toFile(), sonarEnv);

            } else {
                return fail(res, 1, "Unknown project type: not Angular/Maven/Gradle");
            }

            res.setExitCode(exit);
            res.setOutput(logs.toString());
            res.setStatus(exit == 0 ? "SUCCESS" : "FAIL");  // ← ส่งงานเข้า SonarQube แล้ว

            // 4) ไม่ดึง QG/Metrics ณ ตรงนี้ — รอ Webhook
            if (res.getExitCode() == 0) {
                res.setQualityGate(null);
                res.setMetrics(null);
            }

        } catch (Exception e) {
            res.setExitCode(-1);
            res.setStatus("ERROR");
            res.setOutput(e.getMessage());
        } finally {
            res.setCompletedAt(LocalDateTime.now());
            String logPath = writeLogsToFile(res.getScanId(), logs);
            res.setLogFilePath(logPath);
            if (repoRoot != null) deleteQuietly(repoRoot);
        }

        // บันทึกลง DB ของเรา (automateDB)
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
            e.printStackTrace();
            throw e;
        }

        return res;
    }

    /* ===================== DTO/Helper ===================== */

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
            Files.writeString(f, logs, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
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

    /* ===================== Host commands per stack ===================== */

    // ----- Angular -----
    private List<String> hostNodeInstall(Path repoRoot) {
        // ใช้ npm ci ถ้าพร้อม lockfile, ถ้าไม่ก็ fallback npm install
        return hostShell(npmExec + " ci || " + npmExec + " install");
    }

    private List<String> hostNodeCoverage(Path repoRoot) {
        // พยายามสร้าง lcov ถ้ามี script test (ไม่บังคับผ่าน)
        return hostShell(npmExec + " run test -- --watch=false --code-coverage || " +
                npmExec + " run test -- --code-coverage || true");
    }

    private List<String> hostSonarScanAngular(ScanRequest req, Path repoRoot) {
        List<String> cmd = new ArrayList<>();
        cmd.add(scannerExec);
        cmd.add("-Dsonar.projectKey=" + req.getProjectKey());
        cmd.add("-Dsonar.sources=src");
        cmd.add("-Dsonar.exclusions=**/node_modules/**,**/*.spec.ts");
        cmd.add("-Dsonar.tests=src");
        cmd.add("-Dsonar.test.inclusions=**/*.spec.ts");
        cmd.add("-Dsonar.typescript.lcov.reportPaths=coverage/lcov.info");
        cmd.add("-Dsonar.sourceEncoding=UTF-8");
        if (req.getBranchName() != null && !req.getBranchName().isBlank()) {
            cmd.add("-Dsonar.branch.name=" + req.getBranchName());
        }
        return cmd;
    }

    // ----- Maven -----
    private List<String> hostMavenBuild(Path pomDir) {
        Path mvnwWin = pomDir.resolve("mvnw.cmd");
        Path mvnwNix = pomDir.resolve("mvnw");
        String cmd = Files.exists(mvnwWin) ? "\""+mvnwWin.toAbsolutePath()+"\"" :
                Files.exists(mvnwNix) ? mvnwNix.toAbsolutePath().toString() :
                        mavenExec; // ตกมาที่ค่าจาก config

        // รันผ่าน shell เพื่อให้ .cmd/.bat ทำงานชัวร์บน Windows
        return hostShell(cmd + " -B -DskipTests package");
    }

    private String resolveMvnCmd(Path pomDir) {
        Path mvnwWin = pomDir.resolve("mvnw.cmd");
        Path mvnwNix = pomDir.resolve("mvnw");
        if (Files.exists(mvnwWin)) return "\""+mvnwWin.toAbsolutePath()+"\"";
        if (Files.exists(mvnwNix)) return mvnwNix.toAbsolutePath().toString();
        return mavenExec; // เช่น C:\Program Files\Apache\maven-3.9.9\bin\mvn.cmd หรือ "mvn"
    }

    private List<String> hostMavenSonarGoal(ScanRequest req, Path pomDir) {
        String mvn = resolveMvnCmd(pomDir);
        String branchArg = (req.getBranchName()!=null && !req.getBranchName().isBlank())
                ? " -Dsonar.branch.name=" + req.getBranchName() : "";
        String cmd = mvn + " -B -DskipTests verify org.sonarsource.scanner.maven:sonar-maven-plugin:sonar"
                + " -Dsonar.projectKey=" + req.getProjectKey()
                + " -Dsonar.host.url=" + sonarHostUrl()
                + branchArg
                + " -Dsonar.sourceEncoding=UTF-8";
        return hostShell(cmd);
    }

    // ----- Gradle -----
    private List<String> hostGradleBuild(Path gradleDir) {
        Path gwWin = gradleDir.resolve("gradlew.bat");
        Path gwNix = gradleDir.resolve("gradlew");
        String cmd = Files.exists(gwWin) ? "\""+gwWin.toAbsolutePath()+"\"" :
                Files.exists(gwNix) ? gwNix.toAbsolutePath().toString() :
                        gradleExec;
        return hostShell(cmd + " build -x test");
    }

    private List<String> hostSonarScanGradle(ScanRequest req, Path repoRoot, Path gradleDir) {
        String rel = repoRoot.relativize(gradleDir).toString().replace('\\','/');
        String binaries = rel.isBlank() ? "build/classes/java/main" : rel + "/build/classes/java/main";

        List<String> cmd = new ArrayList<>();
        cmd.add(scannerExec);
        cmd.add("-Dsonar.projectKey=" + req.getProjectKey());
        cmd.add("-Dsonar.sources=.");
        cmd.add("-Dsonar.java.binaries=" + binaries);
        cmd.add("-Dsonar.sourceEncoding=UTF-8");
        if (req.getBranchName() != null && !req.getBranchName().isBlank()) {
            cmd.add("-Dsonar.branch.name=" + req.getBranchName());
        }
        return cmd;
    }

    /* ===================== Shared helpers (Host) ===================== */

    // overload: เดิม + env
    private int runAndCapture(List<String> cmd, StringBuilder logs, File workDir, Map<String,String> env) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        if (workDir != null) pb.directory(workDir);
        if (env != null && !env.isEmpty()) pb.environment().putAll(env);
        Process p = pb.start();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line; while ((line = br.readLine()) != null) logs.append(line).append("\n");
        }
        return p.waitFor();
    }

    /** สะดวกไว้รันคำสั่ง shell เดียว (เช่น "npm ci || npm install") */
    private List<String> hostShell(String script) {
        if (IS_WINDOWS) return List.of("cmd", "/c", script);
        return List.of("bash", "-lc", script);
    }

    private ScanModel fail(ScanModel res, int exit, String message) {
        res.setExitCode(exit);
        res.setStatus("FAIL");
        res.setOutput(message);
        res.setCompletedAt(LocalDateTime.now());
        return res;
    }

    private boolean pingSonar(String host, String token) {
        try {
            String url = host + "/api/server/version";
            String json = httpGetWithToken(url, token); // จะได้เวอร์ชัน เช่น "10.6.0.92116"
            return json != null && !json.isBlank();
        } catch (Exception e) {
            log.warn("Ping failed {}: {}", host, e.toString());
            return false;
        }
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
        ScansEntity entity = scanRepository.findById(id)
                .orElseThrow(() -> new ScanNotFoundException(id));
        return toModel(entity);
    }

    public ScanModel getLogScan(UUID id){
        ScansEntity entity = scanRepository.findById(id)
                .orElseThrow(() -> new ScanNotFoundException(id));

        ScanModel model = toModel(entity);
        model.setLogFilePath(entity.getLogFilePath());
        return model;
    }

    public ScanModel cancelScan(UUID scanId){
        ScansEntity scan = scanRepository.findById(scanId)
                .orElseThrow(() -> new ScanNotFoundException(scanId));

        if (!"RUNNING".equals(scan.getStatus())) {
            throw new ScanCancelConflictException(scanId, scan.getStatus());
        }

        scan.setStatus("CANCELLED");
        scan.setCompletedAt(LocalDateTime.now());
        scanRepository.save(scan);

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
