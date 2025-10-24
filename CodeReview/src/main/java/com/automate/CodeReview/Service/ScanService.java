package com.automate.CodeReview.Service;

import com.automate.CodeReview.Models.ScanLogModel;
import com.automate.CodeReview.Models.ScanModel;
import com.automate.CodeReview.entity.ProjectsEntity;
import com.automate.CodeReview.entity.ScansEntity;
import com.automate.CodeReview.repository.ProjectsRepository;
import com.automate.CodeReview.repository.ScansRepository;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.time.format.DateTimeFormatter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;


import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.*;
@Slf4j
@Service
public class ScanService {

    private final ScansRepository scanRepository;
    private final ProjectsRepository projectRepository;
    private final RepositoryService repositoryService;
    private final WebClient sonarWebClient;


    private static final String BASE_DIR = "C:\\gitpools";
    private static final String SCRIPT_FILENAME = "run_sonar.bat";
    private static final String LOG_BASE = "C:\\scan-logs";

    public ScanService(ScansRepository scanRepository, ProjectsRepository projectRepository, RepositoryService repositoryService, WebClient sonarWebClient) {
        this.scanRepository = scanRepository;
        this.projectRepository = projectRepository;
        this.repositoryService = repositoryService;
        this.sonarWebClient = sonarWebClient;
    }

    // ส่วนของ startScan
    @Value("${app.sonar.token}")
    private String sonarToken;

    @Value("${scan.logs.directory:C:/scan-logs}")
    private String scanLogsDirectory;



    public Map<String, Object> startScan(UUID projectId, String username, String password) {
        log.info("Starting scan for project: {}", projectId);

        // 1. ดึงข้อมูล project
        ProjectsEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found with ID: " + projectId));

        String oldClonePath = project.getClonePath();
        String sonarProjectKey = project.getSonarProjectKey();

        String referenceId = String.format("%s_%d_%d",
                sonarProjectKey,
                System.currentTimeMillis(),
                new Random().nextInt(10000)
        );

        //สร้าง entity
        ScansEntity scan = new ScansEntity();
        scan.setProject(project);
        scan.setStatus("RUNNING");
        scan.setQualityGate("PENDING");
        scan.setStartedAt(LocalDateTime.now());
        scan.setReferenceId(referenceId);
        scan = scanRepository.save(scan);

        UUID scanId = scan.getScanId();
        log.info("Created scan record with ID: {}, reference: {}", scanId, referenceId);

        //สร้าง log path จาก scanId (เก็บใน folder ของโปรเจ็ก)
        String logFileName = String.format("scan_%s.log", scanId);
        Path logFilePath = Paths.get(scanLogsDirectory, sonarProjectKey, logFileName);

        scan.setLogFilePath(logFilePath.toString());
        scanRepository.save(scan);

        log.info("Log file will be saved at: {}", logFilePath);


        // 2. Clone project ใหม่
        try {
            // 5. Clone project ใหม่
            Map<String, Object> cloneResult = repositoryService.cloneRepositoryCmd(
                    projectId, username, password
            );

            String newClonePath = (String) cloneResult.get("directory");
            log.info("Cloned to new directory: {}", newClonePath);

            if (oldClonePath != null && !oldClonePath.isBlank()) {
                deleteOldCloneDirectory(oldClonePath);
                log.info("Deleted old clone directory before new clone: {}", oldClonePath);
            }

            // 6. สร้าง Sonar script
            String projectType = detectProjectType(newClonePath);
            log.info("Detected project type: {}", projectType);


            Path scriptPath = createSonarScriptByType(
                    newClonePath,
                    sonarProjectKey,
                    project.getName(),
                    this.sonarToken,
                    projectType
            );
            log.info("Created Sonar script at: {}", scriptPath);

            // 7. อัพเดท clonePath ใน database
            updateProjectClonePath(projectId, newClonePath);

            // 9. รัน Sonar Analysis พร้อมเขียน log ลงไฟล์
            Map<String, Object> scanResult = runSonarAnalysis(newClonePath, logFilePath, scanId);

            // ดึง analysisId จาก SonarQube ทันที
            if (scanResult.get("success").equals(true)) {
                // Poll หา analysisId พร้อม retry
                String analysisId = pollForAnalysisId(sonarProjectKey, 30); // timeout 30 วินาที
                if (analysisId != null) {
                    scan.setAnalysisId(analysisId);
                    log.info("เซ็ต analysisId ไว้ล่วงหน้า: {} สำหรับ scanId: {}", analysisId, scanId);
                } else {
                    log.warn("ไม่สามารถดึง analysisId ได้ภายในเวลาที่กำหนด");
                }
            }

            // 10. อัพเดท status ของ scan
            scan.setCompletedAt(LocalDateTime.now());

            // เก็บ error message ถ้ามี
            if (scanResult.containsKey("error")) {
                // สามารถเพิ่ม field errorMessage ใน ScansEntity ได้
                log.error("Scan failed: {}", scanResult.get("error"));
            }

            scanRepository.save(scan);

            log.info("Scan completed: scanId={}, status={}", scanId, scan.getStatus());
            log.info("DEBUG: หลัง save analysisId - scanId={}, analysisId={}, status={}",
                    scan.getScanId(), scan.getAnalysisId(), scan.getStatus());

            // 11. Return result
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("scanId", scanId);
            result.put("projectId", projectId);
            result.put("referenceId", referenceId);
            result.put("projectName", project.getName());
            result.put("sonarProjectKey", sonarProjectKey);
            result.put("oldPath", oldClonePath);
            result.put("newPath", newClonePath);
            result.put("lastCommit", cloneResult.get("lastCommit"));
            result.put("scanResult", scanResult);
            result.put("logFilePath", logFilePath.toString());
            result.put("status", scan.getStatus());

            return result;

        } catch (Exception e) {
            log.error("Scan failed for project: {}", projectId, e);

            // ลบ scan record ที่ล้มเหลว
            if (scan != null && scan.getScanId() != null) {
                try {
                    scanRepository.delete(scan);
                    log.info("Deleted failed scan: {}", scan.getScanId());
                } catch (Exception deleteEx) {
                    log.error("Failed to delete scan", deleteEx);
                }
            }

            // เขียน error ลงไฟล์ log
            if (logFilePath != null) {
                try {
                    Files.createDirectories(logFilePath.getParent());
                    Files.writeString(logFilePath,
                            String.format("=== SCAN FAILED ===\n%s\n%s\n%s\n",
                                    LocalDateTime.now(),
                                    e.getClass().getSimpleName(),
                                    e.getMessage()),
                            StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.APPEND);
                } catch (IOException ignored) {}
            }

            throw new RuntimeException("Scan failed: " + e.getMessage(), e);
        }
    }

    //Update ClonePath
    @Transactional
    public void updateProjectClonePath(UUID projectId, String newClonePath) {
        ProjectsEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));
        project.setClonePath(newClonePath);
        projectRepository.save(project);
        log.info("Updated clone path for project: {}", projectId);
    }

    //detect
    private String detectProjectType(String clonePath) {
        Path startPath = Paths.get(clonePath);

        // 1. สแกนหา Path ของไฟล์ config ที่สนใจทั้งหมดในครั้งเดียว
        List<Path> relevantFiles;
        try (Stream<Path> walk = Files.walk(startPath, 3)) {
            relevantFiles = walk
                    .filter(p -> p.endsWith("pom.xml") ||
                            p.endsWith("angular.json") ||
                            p.endsWith("package.json") ||
                            p.endsWith("build.gradle") ||
                            p.endsWith("build.gradle.kts")) // เพิ่ม Gradle เข้ามาเพื่อความสมบูรณ์
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Error walking directory: " + startPath, e);
            return "UNKNOWN"; // หากเกิดข้อผิดพลาดในการอ่าน directory
        }

        // 2. ตรวจสอบประเภทโปรเจกต์ตามลำดับความสำคัญจาก List ที่ได้มา

        // A. Maven (pom.xml)
        if (relevantFiles.stream().anyMatch(p -> p.endsWith("pom.xml"))) {
            return "MAVEN";
        }

        // B. Gradle (build.gradle/kts)
        if (relevantFiles.stream().anyMatch(p -> p.endsWith("build.gradle") || p.endsWith("build.gradle.kts"))) {
            return "GRADLE";
        }

        // C. Angular (angular.json)
        if (relevantFiles.stream().anyMatch(p -> p.endsWith("angular.json"))) {
            return "ANGULAR";
        }

        // D. Node.js (package.json) - ไม่จำเป็นต้องอ่านเนื้อหาแล้ว เพราะเราถือว่า package.json = NODE
        if (relevantFiles.stream().anyMatch(p -> p.endsWith("package.json"))) {
            return "NODE";
        }

        return "UNKNOWN";
    }

    private Path createSonarScriptByType(String clonePath, String projectKey,
                                         String projectName, String sonarToken,
                                         String projectType) throws IOException {
        switch (projectType) {
            case "MAVEN":
                return repositoryService.createSonarScriptMaven(clonePath, projectKey, projectName, sonarToken);
            case "ANGULAR":
                return repositoryService.createSonarScriptAngular(clonePath, projectKey, projectName, sonarToken);
            case "NODE":
                return repositoryService.createSonarScriptNode(clonePath, projectKey, projectName, sonarToken);
            default:
                throw new IllegalStateException("Unsupported project type: " + projectType);
        }
    }

    /**
     * ลบ directory เก่า
     */
    private void deleteOldCloneDirectory(String directoryPath) {
        try {
            Path path = Paths.get(directoryPath);
            if (Files.exists(path)) {
                log.info("Deleting old clone directory: {}", directoryPath);

                // ใช้ cmd.exe /c rmdir /s /q แทน Java Files API
                // เพราะ Windows จะ handle file locks ได้ดีกว่า
                List<String> command = Arrays.asList(
                        "cmd.exe", "/c",
                        "rmdir", "/s", "/q",
                        directoryPath
                );

                ProcessBuilder pb = new ProcessBuilder(command);
                Process p = pb.start();
                boolean finished = p.waitFor(30, TimeUnit.SECONDS);

                if (finished && p.exitValue() == 0) {
                    log.info("Successfully deleted: {}", directoryPath);
                } else {
                    log.warn("Failed to delete directory (exit code: {}), but continuing...",
                            finished ? p.exitValue() : "timeout");
                }
            } else {
                log.warn("Old clone directory not found: {}", directoryPath);
            }
        } catch (Exception e) {
            log.error("Failed to delete old clone directory: {}", directoryPath, e);
            // ไม่ throw exception เพราะไม่อยากให้ scan ล้มเหลว
        }
    }

    private String fetchLatestAnalysisId(String projectKey) {
        try {
            // เรียก SonarQube API เพื่อดึง analysis ล่าสุด
            JsonNode response = sonarWebClient.get()
                    .uri(u -> u.path("/api/project_analyses/search")
                            .queryParam("project", projectKey)
                            .queryParam("ps", 1)
                            .build())
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response != null) {
                JsonNode analyses = response.path("analyses");
                if (analyses.isArray() && analyses.size() > 0) {
                    return analyses.get(0).path("key").asText(null);
                }
            }
        } catch (Exception e) {
            log.error("Failed to fetch analysisId", e);
        }
        return null;
    }


    /**
     * รัน Sonar Analysis Script
     */
    private Map<String, Object> runSonarAnalysis(String clonePath, Path logFilePath, UUID scanId) {
        Map<String, Object> result = new LinkedHashMap<>();
        Path scriptPath = Paths.get(clonePath, SCRIPT_FILENAME);

        if (!Files.exists(scriptPath)) {
            log.error("Sonar script not found at: {}", scriptPath);
            result.put("success", false);
            result.put("error", "Sonar script not found");
            return result;
        }

        log.info("Running Sonar analysis for scan: {}", scanId);
        log.info("Script path: {}", scriptPath);

        List<String> command = List.of(
                "cmd.exe", "/c",
                "\"" + scriptPath.toString() + "\""
        );

        try {
            int exitCode = runScriptAndLog(command, new File(clonePath),
                    Duration.ofMinutes(30), logFilePath, scanId);

            result.put("success", exitCode == 0);
            result.put("exitCode", exitCode);

            if (exitCode == 0) {
                log.info("Sonar analysis completed successfully for scan: {}", scanId);
            } else {
                log.error("Sonar analysis failed with exit code: {} for scan: {}", exitCode, scanId);
                result.put("error", "Script exited with code " + exitCode);
            }
        } catch (Exception e) {
            log.error("Error running Sonar analysis for scan: {}", scanId, e);
            result.put("success", false);
            result.put("exitCode", -1);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * รัน script และ log output
     */
    private int runScriptAndLog(List<String> cmd, File workDir, Duration timeout,
                                Path logFilePath, UUID scanId) {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(workDir);
        pb.redirectErrorStream(true);

        String printable = String.join(" ", cmd);
        log.info("EXEC: {}", printable);
        log.info("Log file: {}", logFilePath);

        ExecutorService ex = null;
        BufferedWriter fileWriter = null;

        try {
            // สร้างไฟล์ log
            Files.createDirectories(logFilePath.getParent());
            fileWriter = Files.newBufferedWriter(logFilePath, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            // เขียน header พร้อม scanId
            fileWriter.write("=".repeat(70) + "\n");
            fileWriter.write("           SONARQUBE ANALYSIS LOG\n");
            fileWriter.write("=".repeat(70) + "\n");
            fileWriter.write("Scan ID         : " + scanId + "\n");
            fileWriter.write("Started at      : " + LocalDateTime.now() + "\n");
            fileWriter.write("Command         : " + printable + "\n");
            fileWriter.write("Working Dir     : " + workDir.getAbsolutePath() + "\n");
            fileWriter.write("Timeout         : " + timeout.toMinutes() + " minutes\n");
            fileWriter.write("=".repeat(70) + "\n\n");
            fileWriter.flush();

            Process p = pb.start();

            BufferedWriter finalFileWriter = fileWriter;
            try (var reader = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {

                ex = Executors.newSingleThreadExecutor();
                Future<?> f = ex.submit(() -> {
                    try {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            // Log to console
                            log.info("[sonar] {}", line);

                            // Log to file (with timestamp)
                            synchronized (finalFileWriter) {
                                finalFileWriter.write(
                                        LocalDateTime.now().format(
                                                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
                                        ) + " | " + line
                                );
                                finalFileWriter.newLine();
                                finalFileWriter.flush();
                            }
                        }
                    } catch (IOException e) {
                        log.warn("Error reading/writing process output", e);
                    }
                });

                boolean finished = p.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
                if (!finished) {
                    p.destroyForcibly();
                    String timeoutMsg = "⏰ Process timeout after " + timeout.toMinutes() + " minutes";
                    log.error(timeoutMsg);
                    fileWriter.write("\n" + "=".repeat(70) + "\n");
                    fileWriter.write(timeoutMsg + "\n");
                    fileWriter.flush();
                    throw new IllegalStateException("Process timeout: " + printable);
                }

                // รอให้อ่าน output จบ
                try {
                    f.get(1, TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                    log.warn("Timeout waiting for output reader to finish");
                }

                int exit = p.exitValue();
                log.info("EXEC EXIT = {}", exit);

                // เขียน footer
                fileWriter.write("\n" + "=".repeat(70) + "\n");
                fileWriter.write("Process completed\n");
                fileWriter.write("Exit Code       : " + exit + "\n");
                fileWriter.write("Status          : " + (exit == 0 ? "✅ SUCCESS" : "❌ FAILED") + "\n");
                fileWriter.write("Completed at    : " + LocalDateTime.now() + "\n");
                fileWriter.write("=".repeat(70) + "\n");
                fileWriter.flush();

                return exit;
            }
        } catch (Exception e) {
            log.error("EXEC error", e);

            // เขียน error ลงไฟล์
            if (fileWriter != null) {
                try {
                    fileWriter.write("\n" + "=".repeat(70) + "\n");
                    fileWriter.write("❌ ERROR OCCURRED\n");
                    fileWriter.write("=".repeat(70) + "\n");
                    fileWriter.write("Error at        : " + LocalDateTime.now() + "\n");
                    fileWriter.write("Error Type      : " + e.getClass().getSimpleName() + "\n");
                    fileWriter.write("Error Message   : " + e.getMessage() + "\n");
                    if (e.getCause() != null) {
                        fileWriter.write("Caused by       : " + e.getCause() + "\n");
                    }
                    fileWriter.write("=".repeat(70) + "\n");
                    fileWriter.flush();
                } catch (IOException ignored) {}
            }

            return -1;
        } finally {
            // ปิด file writer
            if (fileWriter != null) {
                try {
                    fileWriter.close();
                    log.info("Log file closed: {}", logFilePath);
                } catch (IOException e) {
                    log.error("Failed to close log file", e);
                }
            }

            // ปิด thread pool
            if (ex != null) {
                ex.shutdownNow();
                try {
                    ex.awaitTermination(1, TimeUnit.SECONDS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private String pollForAnalysisId(String projectKey, int timeoutSeconds) {
        long endTime = System.currentTimeMillis() + (timeoutSeconds * 1000L);
        int attempt = 0;

        while (System.currentTimeMillis() < endTime) {
            attempt++;
            try {
                String analysisId = fetchLatestAnalysisId(projectKey);
                if (analysisId != null && !analysisId.isBlank()) {
                    log.info("✅ พบ analysisId ในครั้งที่ {}: {}", attempt, analysisId);
                    return analysisId;
                }

                if (attempt == 1) {
                    log.info("🔄 กำลังรอ analysisId จาก SonarQube...");
                }

                Thread.sleep(2000); // รอ 2 วินาที

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("⚠️ Polling ถูกขัดจังหวะ");
                return null;
            } catch (Exception e) {
                log.error("Error polling analysisId (attempt {}): {}", attempt, e.getMessage());
            }
        }

        log.error("⏰ Timeout: ไม่พบ analysisId หลังจาก {} วินาที", timeoutSeconds);
        return null;
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
            model.setMetrics(scanEntity.getMetrics());
            model.setLogFilePath(String.valueOf(scanEntity.getLogFilePath()));
            model.setMaintainabilityGate(String.valueOf(scanEntity.getMaintainabilityGate()));
            model.setReliabilityGate(String.valueOf(scanEntity.getReliabilityGate()));
            model.setSecurityGate(String.valueOf(scanEntity.getSecurityGate()));
            model.setSecurityReviewGate(String.valueOf(scanEntity.getSecurityReviewGate()));
            scansModel.add(model);
        }
        return scansModel;
    }

    public ScanModel getByIdScan(UUID scanId){
        ScansEntity scan = scanRepository.findById(scanId)
                .orElse(null);

        ScanModel model = new ScanModel();
        model.setScanId(scan.getScanId());
        model.setProjectId(scan.getProject().getProjectId());
        model.setStatus(scan.getStatus());
        model.setStartedAt(scan.getStartedAt());
        model.setCompletedAt(scan.getCompletedAt());
        model.setQualityGate(String.valueOf(scan.getQualityGate()));
        model.setMetrics(scan.getMetrics());
        model.setLogFilePath(String.valueOf(scan.getLogFilePath()));
        model.setMaintainabilityGate(String.valueOf(scan.getMaintainabilityGate()));
        model.setReliabilityGate(String.valueOf(scan.getReliabilityGate()));
        model.setSecurityGate(String.valueOf(scan.getSecurityGate()));
        model.setSecurityReviewGate(String.valueOf(scan.getSecurityReviewGate()));
        return model;
    }

    public ScanModel getLogScan(UUID id){
        return null;
    }


    public ScanLogModel getScanLogById(UUID scanId) {
        try {
            Path logPath = findLogFlie(scanId);

            if(logPath == null || !Files.exists(logPath)){
                throw new IllegalArgumentException("Scan Log Not Found in Id: " + scanId);
            }

            List<String> lines = Files.readAllLines(logPath, StandardCharsets.UTF_8);

            ScanLogModel logModel = new ScanLogModel();
            logModel.setScanId(scanId);
            logModel.setLines(lines);
            return logModel;
        }catch (IOException e){
            throw new RuntimeException("Error reading scan log: " + e.getMessage(), e);
        }
    }

    private Path findLogFlie(UUID scanId) throws IOException {
        String logFileName = "scan_" + scanId + ".log";
        Path logDir = Paths.get(LOG_BASE);

        if (!Files.exists(logDir)) {
            return null;
        }

        try (Stream<Path> paths = Files.walk(logDir,2)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals(logFileName))
                    .findFirst()
                    .orElse(null);
        }
    }

}
