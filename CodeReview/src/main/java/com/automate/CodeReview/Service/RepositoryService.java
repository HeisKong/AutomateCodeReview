package com.automate.CodeReview.Service;

import com.automate.CodeReview.Models.RepositoryModel;
import com.automate.CodeReview.dto.RepositoryCreateRequest;
import com.automate.CodeReview.dto.RepositoryResponse;
import com.automate.CodeReview.entity.ProjectsEntity;
import com.automate.CodeReview.entity.UsersEntity;
import com.automate.CodeReview.exception.*;
import com.automate.CodeReview.repository.ProjectsRepository;
import com.automate.CodeReview.repository.UsersRepository;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.format.DateTimeFormatter;
import java.time.LocalDate;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import java.time.LocalDateTime;

@Service
@Slf4j
public class RepositoryService {

    private final ProjectsRepository projectsRepository;
    private final UsersRepository usersRepository;
    private final NotiService notiService;

    private static final String BASE_DIR = "C:\\gitpools";
    private static final String SCRIPT_FILENAME = "run_sonar.bat";

    @Value("${app.sonar.token}")
    private String sonarToken;

    public RepositoryService(ProjectsRepository projectsRepository, UsersRepository usersRepository, NotiService notiService) {
        this.projectsRepository = projectsRepository;
        this.usersRepository = usersRepository;
        this.notiService = notiService;
    }

    // CREATE
    @Transactional
    public RepositoryResponse createRepository(RepositoryCreateRequest req) {
        if (req.getUser() == null) {
            throw new IllegalArgumentException("userId is required");
        }

        boolean valid = checkRepo(req.getRepositoryUrl(), req.getUsername(), req.getPassword());
        if (!valid) throw new IllegalArgumentException("Invalid repository or authentication failed");

        UUID userId = req.getUser();
        UsersEntity user = usersRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        ProjectsEntity project = new ProjectsEntity();
        project.setProjectId(UUID.randomUUID());
        project.setUser(user);
        project.setName(req.getName());
        project.setRepositoryUrl(req.getRepositoryUrl());
        project.setSonarProjectKey(req.getName());
        project.setProjectType(req.getProjectType());
        project.setCreatedAt(LocalDateTime.now());
        project.setUpdatedAt(LocalDateTime.now());

        ProjectsEntity saved = projectsRepository.save(project);

        Map<String, Object> cloneResult = cloneRepositoryCmd(
                saved.getProjectId(),
                req.getUsername(),
                req.getPassword()
        );

        String clonePath = (String) cloneResult.get("directory");

        Path scriptLocation = null;
        try {
            scriptLocation = createSonarScript(
                    clonePath,
                    saved.getSonarProjectKey(),
                    saved.getName(),
                    this.sonarToken
            );
        } catch (IOException e) {
            log.error("Failed to create Sonar script for project: {}", saved.getProjectId(), e);
            // 2. Rollback: โยน RuntimeException ออกไปเพื่อให้ Spring Rollback Transaction
            // (เพราะถ้าไม่มีสคริปต์ ก็ไม่ควรจะถือว่าการเพิ่ม Project สำเร็จสมบูรณ์)
            throw new RuntimeException("Could not create Sonar analysis script.", e);
        }

        saved.setClonePath(clonePath);
        saved.setScript(true);

        projectsRepository.save(saved);


        //ล้าง credential (ใน memory)
        req.setUsername(null);
        req.setPassword(null);


        notiService.createRepoNotiAsync(saved.getProjectId(), "Add Repo Success!");

        return new RepositoryResponse(
                saved.getProjectId(),
                saved.getUser().getUserId(),
                saved.getName(),
                saved.getRepositoryUrl(),
                saved.getProjectType(),
                saved.getSonarProjectKey(),
                saved.getCreatedAt(),
                saved.getUpdatedAt(),
                saved.getClonePath()
        );
    }

    //check gitUrl
    private boolean checkRepo(String url, String username, String password) {
        try {
            String authUrl = url.replace("https://", "https://" + username + ":" + password + "@");
            Process p = new ProcessBuilder("git", "ls-remote", authUrl).start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    //clone git
    @Transactional(readOnly = true)
    public Map<String, Object> cloneRepositoryCmd(UUID projectId, String username, String password) {
        ProjectsEntity project = projectsRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));

        String repoUrl = project.getRepositoryUrl();
        String projectName = sanitizeProjectName(project.getName());
        String yymmdd = LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
        String targetFolderName = resolveNextSeqFolder(BASE_DIR, projectName, yymmdd); // returns like "TaxCalculations_251016_00"

        Path targetDir = Paths.get(BASE_DIR, targetFolderName);

        // สร้าง base และโฟลเดอร์เป้าหมาย
        try {
            Files.createDirectories(targetDir.getParent() != null ? targetDir.getParent() : Paths.get(BASE_DIR));
        } catch (IOException e) {
            throw new RuntimeException("Cannot create base dir " + BASE_DIR, e);
        }

        // สร้าง auth URL แบบชั่วคราว (เฉพาะตอน clone)
        String authUrl = buildAuthUrl(repoUrl, username, password);

        // ใช้ cmd.exe /c git clone --depth 1 <authUrl> "<targetDir>"
        // ใช้ arguments แยกคำ เพื่อลดปัญหา quoting
        List<String> command = Arrays.asList(
                "cmd.exe", "/c",
                "git", "clone", "--depth", "1", authUrl, targetDir.toString()
        );

        int exit = runAndLog(command, new File(BASE_DIR), Duration.ofMinutes(5));
        if (exit != 0) {
            throw new IllegalStateException("Clone failed (exit=" + exit + ")");
        }

        // อ่าน last commit (optional): git -C "<dir>" rev-parse HEAD
        String lastCommit = runAndCaptureSingleLine(
                Arrays.asList("cmd.exe", "/c", "git", "-C", targetDir.toString(), "rev-parse", "HEAD"),
                new File(BASE_DIR),
                Duration.ofSeconds(30)
        );

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("projectId", projectId);
        resp.put("name", project.getName());
        resp.put("repositoryUrl", repoUrl);
        resp.put("directory", targetDir.toString());
        resp.put("lastCommit", lastCommit);
        resp.put("clonedAt", Instant.now());
        return resp;
    }

    // ---------- Helpers ----------

    private String sanitizeProjectName(String name) {
        // อนุญาตเฉพาะ a-z0-9_- และแทนที่ช่องว่างด้วย _
        String safe = name.trim().replaceAll("\\s+", "_");
        safe = safe.replaceAll("[^A-Za-z0-9_\\-]", "");
        if (safe.isBlank()) safe = "project";
        return safe;
    }

    /**
     * หาโฟลเดอร์ถัดไป: <project>_<yymmdd>_<seq 00-99> ที่ยังไม่ถูกใช้งาน
     */
    private String resolveNextSeqFolder(String baseDir, String project, String yymmdd) {
        for (int i = 0; i < 100; i++) {
            String seq = String.format("%02d", i);
            String candidate = project + "_" + yymmdd + "_" + seq;
            Path p = Paths.get(baseDir, candidate);
            if (!Files.exists(p)) {
                return candidate;
            }
        }
        throw new IllegalStateException("No available sequence (00-99) for " + project + " on " + yymmdd);
    }

    private String buildAuthUrl(String url, String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return url; // public repo
        }
        // แทรก credential แบบ https://user:token@github.com/owner/repo.git
        return url.replace("https://", "https://" + urlEncode(username) + ":" + urlEncode(password) + "@");
    }

    private String urlEncode(String s) {
        try { return URLEncoder.encode(s, StandardCharsets.UTF_8.name()); }
        catch (Exception e) { return s; }
    }

    /**
     * รันคำสั่งและ log stdout/stderr (mask token ในบรรทัดที่มี '@')
     */
    private int runAndLog(List<String> cmd, File workDir, Duration timeout) {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(workDir);
        pb.redirectErrorStream(true); // รวม stderr เข้ากับ stdout จะได้อ่านทีเดียว

        String printable = String.join(" ", cmd).replaceAll("https://[^@]+@", "https://***@");
        log.info("EXEC: {}", printable);

        try {
            Process p = pb.start();
            var reader = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));

            ExecutorService ex = Executors.newSingleThreadExecutor();
            Future<?> f = ex.submit(() -> {
                String line;
                try {
                    while ((line = reader.readLine()) != null) {
                        log.info("[git] {}", line.replaceAll("https://[^@]+@", "https://***@"));
                    }
                } catch (IOException ignore) {}
            });

            boolean finished = p.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                p.destroyForcibly();
                ex.shutdownNow();
                throw new IllegalStateException("Process timeout: " + printable);
            }
            f.get(1, TimeUnit.SECONDS);
            ex.shutdown();

            int exit = p.exitValue();
            log.info("EXEC EXIT = {}", exit);
            return exit;
        } catch (Exception e) {
            log.error("EXEC error", e);
            return -1;
        }
    }

    private String runAndCaptureSingleLine(List<String> cmd, File workDir, Duration timeout) {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(workDir);
        pb.redirectErrorStream(true);
        String printable = String.join(" ", cmd).replaceAll("https://[^@]+@", "https://***@");

        try {
            Process p = pb.start();
            boolean finished = p.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                p.destroyForcibly();
                throw new IllegalStateException("Process timeout: " + printable);
            }
            try (var reader = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String out = reader.lines().findFirst().orElse("");
                return out.trim();
            }
        } catch (Exception e) {
            log.warn("EXEC capture error: {}", printable, e);
            return "";
        }
    }

    //ตราจหา mvn or angular
    private String detectProjectType(String clonePath) {
        Path startPath = Paths.get(clonePath);

        // 1. สแกนหา Path ของไฟล์ config ที่สนใจทั้งหมดในครั้งเดียว
        List<Path> relevantFiles;
        try (Stream<Path> walk = Files.walk(startPath, 2)) {
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

    private Path createSonarScript(String clonePath, String projectKey, String projectName, String sonarToken) throws IOException {
        String projectType = detectProjectType(clonePath);

        log.info("Detected project type: {}", projectType);

        switch (projectType) {
            case "MAVEN":
                return createSonarScriptMaven(clonePath, projectKey, projectName, sonarToken);
            case "ANGULAR":
                return createSonarScriptAngular(clonePath, projectKey, projectName, sonarToken);
//            case "GRADLE":
//                return createSonarScriptGradle(clonePath, projectKey, projectName, sonarToken);
            case "NODE":
                return createSonarScriptNode(clonePath, projectKey, projectName, sonarToken);
            default:
                throw new IllegalStateException("Unknown project type. Cannot create Sonar script.");
        }
    }

    //สร้างไฟล์ .bat
    public Path createSonarScriptMaven(String clonePath, String projectKey, String projectName, String sonarToken) throws IOException {
        Path scriptPath = Paths.get(clonePath, SCRIPT_FILENAME);

        // การ Escape สำหรับ Batch Script: %% -> % ใน Batch
        String scriptContent = String.format(
                "@echo off\r\n" +
                        "setlocal EnableExtensions EnableDelayedExpansion\r\n" +
                        "REM Script created at %s\r\n" +
                        "\r\n" +
                        "REM ===== Default values from generator (ถูก override ได้ด้วย args) =====\r\n" +
                        "set \"SONAR_TOKEN=%s\"\r\n" +
                        "set \"PROJECT_KEY=%s\"\r\n" +
                        "set \"PROJECT_NAME=%s\"\r\n" +
                        "\r\n" +
                        "REM ===== Allow override by args: 1=token 2=projectKey 3=projectName =====\r\n" +
                        "if not \"%%~1\"==\"\" set \"SONAR_TOKEN=%%~1\"\r\n" +
                        "if not \"%%~2\"==\"\" set \"PROJECT_KEY=%%~2\"\r\n" +
                        "if not \"%%~3\"==\"\" set \"PROJECT_NAME=%%~3\"\r\n" +
                        "\r\n" +
                        "REM ===== Find pom.xml starting from this script directory (Guarantees to find pom.xml in nested structure) =====\r\n" +
                        "set \"ROOT=%%~dp0\"\r\n" +
                        "set \"POM=\"\r\n" +
                        "if exist \"%%ROOT%%pom.xml\" (\r\n" +
                        "  set \"POM=%%ROOT%%pom.xml\"\r\n" +
                        ") else (\r\n" +
                        "  for /r \"%%ROOT%%\" %%%%F in (pom.xml) do (\r\n" +
                        "    REM ให้คะแนนไฟล์ที่มีโฟลเดอร์ src ข้าง ๆ ก่อน เพื่อเลี่ยง pom ระดับ aggregator\r\n" +
                        "    if exist \"%%%%~dpFsrc\" (\r\n" +
                        "      set \"POM=%%%%~fF\"\r\n" +
                        "      goto :pom_found\r\n" +
                        "    )\r\n" +
                        "    if not defined POM set \"POM=%%%%~fF\"\r\n" +
                        "  )\r\n" +
                        ")\r\n" +
                        ":pom_found\r\n" +
                        "if not defined POM (\r\n" +
                        "  echo [ERROR] No pom.xml found under \"%%ROOT%%\"\r\n" +
                        "  pause\r\n" +
                        "  exit /b 1\r\n" +
                        ")\r\n" +
                        "echo Using POM: \"%%POM%%\"\r\n" +
                        "\r\n" +
                        "REM ===== Prefer mvnw if present or fall back to mvn =====\r\n" +
                        "set \"MVN=mvn\"\r\n" +
                        "set \"POM_DIR=\"\r\n" +
                        "set \"USING_WRAPPER=0\"\r\n" +
                        "for %%%%X in (\"!POM!\") do (\r\n" +
                        "  set \"POM_DIR=%%%%~dpX\"\r\n" +
                        ")\r\n" +
                        "if exist \"!POM_DIR!mvnw.cmd\" (\r\n" +
                        "  set \"MVN=!POM_DIR!mvnw.cmd\"\r\n" +
                        "  set \"USING_WRAPPER=1\"\r\n" +
                        ")\r\n" +
                        "if not defined POM_DIR set \"POM_DIR=!ROOT!\"\r\n" +
                        "if \"!USING_WRAPPER!\"==\"0\" echo INFO: Using system Maven. Make sure it is installed and in PATH.\r\n" +
                        "\r\n" +
                        "echo Starting Sonar Analysis...\r\n" +
                        "\r\n" +
                        "\"!MVN!\" -f \"!POM!\" clean verify sonar:sonar -Dsonar.token=\"!SONAR_TOKEN!\" -Dsonar.host.url=http://localhost:9000 -Dsonar.projectKey=\"!PROJECT_KEY!\" -Dsonar.projectName=\"!PROJECT_NAME!\" -Dsonar.projectBaseDir=\"!POM_DIR!\"\r\n" +
                        "\r\n" +
                        "if errorlevel 1 (\r\n" +
                        "  echo [ERROR] Sonar Analysis FAILED! Exit Code: !errorlevel!\r\n" +
                        "  pause\r\n" +
                        "  exit /b 1\r\n" +
                        ")\r\n" +
                        "echo Sonar Analysis COMPLETED successfully.\r\n" +
                        "pause\r\n" +
                        "endlocal & exit /b 0\r\n",
                LocalDateTime.now(),
                sonarToken,
                projectKey,
                projectName
        );

        Files.writeString(scriptPath, scriptContent);
        log.info("Created Sonar script at: {}", scriptPath);
        return scriptPath;
    }

    //angular
    public Path createSonarScriptAngular(String clonePath, String projectKey, String projectName, String sonarToken) throws IOException {
        Path scriptPath = Paths.get(clonePath, SCRIPT_FILENAME);

        String scriptContent = String.format(
                "@echo off\r\n" +
                        "setlocal EnableExtensions EnableDelayedExpansion\r\n" +
                        "REM Script created at %s\r\n" +
                        "\r\n" +
                        "REM ===== Default values from generator (ถูก override ได้ด้วย args) =====\r\n" +
                        "set \"SONAR_TOKEN=%s\"\r\n" +
                        "set \"PROJECT_KEY=%s\"\r\n" +
                        "set \"PROJECT_NAME=%s\"\r\n" +
                        "\r\n" +
                        "REM ===== Allow override by args: 1=token 2=projectKey 3=projectName =====\r\n" +
                        "if not \"%%~1\"==\"\" set \"SONAR_TOKEN=%%~1\"\r\n" +
                        "if not \"%%~2\"==\"\" set \"PROJECT_KEY=%%~2\"\r\n" +
                        "if not \"%%~3\"==\"\" set \"PROJECT_NAME=%%~3\"\r\n" +
                        "\r\n" +
                        "REM ===== Find package.json or angular.json starting from this script directory =====\r\n" +
                        "set \"ROOT=%%~dp0\"\r\n" +
                        "set \"PROJECT_DIR=\"\r\n" +
                        "if exist \"%%ROOT%%package.json\" (\r\n" +
                        "  set \"PROJECT_DIR=%%ROOT%%\"\r\n" +
                        ") else (\r\n" +
                        "  for /r \"%%ROOT%%\" %%%%F in (angular.json) do (\r\n" +
                        "    if exist \"%%%%~dpFpackage.json\" (\r\n" +
                        "      set \"PROJECT_DIR=%%%%~dpF\"\r\n" +
                        "      goto :project_found\r\n" +
                        "    )\r\n" +
                        "    if not defined PROJECT_DIR set \"PROJECT_DIR=%%%%~dpF\"\r\n" +
                        "  )\r\n" +
                        ")\r\n" +
                        ":project_found\r\n" +
                        "if not defined PROJECT_DIR (\r\n" +
                        "  echo [ERROR] No Angular project found under \"%%ROOT%%\"\r\n" +
                        "  pause\r\n" +
                        "  exit /b 1\r\n" +
                        ")\r\n" +
                        "echo Using Project Directory: \"%%PROJECT_DIR%%\"\r\n" +
                        "\r\n" +
                        "REM ===== Change to project directory =====\r\n" +
                        "cd /d \"%%PROJECT_DIR%%\"\r\n" +
                        "\r\n" +
                        "REM ===== Check if npm is installed =====\r\n" +
                        "where npm >nul 2>&1\r\n" +
                        "if errorlevel 1 (\r\n" +
                        "  echo [ERROR] npm not found in PATH.\r\n" +
                        "  echo Please install Node.js from: https://nodejs.org/\r\n" +
                        "  pause\r\n" +
                        "  exit /b 1\r\n" +
                        ")\r\n" +
                        "\r\n" +
                        "REM ===== Check if node_modules exists, if not run npm install =====\r\n" +
                        "if not exist \"node_modules\" (\r\n" +
                        "  echo node_modules not found. Running npm install...\r\n" +
                        "  call npm install\r\n" +
                        "  if errorlevel 1 (\r\n" +
                        "    echo [ERROR] npm install FAILED!\r\n" +
                        "    pause\r\n" +
                        "    exit /b 1\r\n" +
                        "  )\r\n" +
                        ")\r\n" +
                        "\r\n" +
                        "REM ===== Check if sonar-scanner is installed =====\r\n" +
                        "where sonar-scanner >nul 2>&1\r\n" +
                        "if errorlevel 1 (\r\n" +
                        "  echo [ERROR] sonar-scanner not found in PATH.\r\n" +
                        "  echo Please install sonar-scanner: npm install -g sonarqube-scanner\r\n" +
                        "  echo Or download from: https://docs.sonarqube.org/latest/analysis/scan/sonarscanner/\r\n" +
                        "  pause\r\n" +
                        "  exit /b 1\r\n" +
                        ")\r\n" +
                        "\r\n" +
                        "echo Starting Sonar Analysis for Angular project...\r\n" +
                        "\r\n" +
                        "sonar-scanner -Dsonar.projectKey=\"!PROJECT_KEY!\" -Dsonar.projectName=\"!PROJECT_NAME!\" -Dsonar.sources=src -Dsonar.host.url=http://localhost:9000 -Dsonar.token=\"!SONAR_TOKEN!\" -Dsonar.projectBaseDir=\"!PROJECT_DIR!\"\r\n" +
                        "\r\n" +
                        "if errorlevel 1 (\r\n" +
                        "  echo [ERROR] Sonar Analysis FAILED! Exit Code: !errorlevel!\r\n" +
                        "  pause\r\n" +
                        "  exit /b 1\r\n" +
                        ")\r\n" +
                        "echo Sonar Analysis COMPLETED successfully.\r\n" +
                        "pause\r\n" +
                        "endlocal & exit /b 0\r\n",
                LocalDateTime.now(),
                sonarToken,
                projectKey,
                projectName
        );

        Files.writeString(scriptPath, scriptContent);
        log.info("Created Sonar script for Angular at: {}", scriptPath);
        return scriptPath;
    }

    //node
    public Path createSonarScriptNode(String clonePath, String projectKey, String projectName, String sonarToken) throws IOException {
        Path projectPath = Paths.get(clonePath);

        // 1. สร้างไฟล์ sonar-project.properties
        Path propertiesPath = projectPath.resolve("sonar-project.properties");
        String propertiesContent = String.format(
                "sonar.projectKey=%s\n" +
                        "sonar.projectName=%s\n" +
                        "sonar.sources=.\n" +  // เปลี่ยนจาก src เป็น .
                        "sonar.host.url=http://localhost:9000\n" +
                        "sonar.token=%s\n",
                projectKey,
                projectName,
                sonarToken
        );
        Files.writeString(propertiesPath, propertiesContent);
        log.info("Created sonar-project.properties at: {}", propertiesPath);

        // 2. สร้าง batch script
        Path scriptPath = projectPath.resolve(SCRIPT_FILENAME);
        String scriptContent = String.format(
                "@echo off\r\n" +
                        "setlocal EnableExtensions EnableDelayedExpansion\r\n" +
                        "REM Script created at %s\r\n" +
                        "\r\n" +
                        "REM ===== Find project directory =====\r\n" +
                        "set \"ROOT=%%~dp0\"\r\n" +
                        "set \"PROJECT_DIR=\"\r\n" +
                        "if exist \"%%ROOT%%package.json\" (\r\n" +
                        "  set \"PROJECT_DIR=%%ROOT%%\"\r\n" +
                        ") else (\r\n" +
                        "  for /r \"%%ROOT%%\" %%%%F in (package.json) do (\r\n" +
                        "    if exist \"%%%%~dpFsrc\" (\r\n" +
                        "      set \"PROJECT_DIR=%%%%~dpF\"\r\n" +
                        "      goto :project_found\r\n" +
                        "    )\r\n" +
                        "    if not defined PROJECT_DIR set \"PROJECT_DIR=%%%%~dpF\"\r\n" +
                        "  )\r\n" +
                        ")\r\n" +
                        ":project_found\r\n" +
                        "if not defined PROJECT_DIR (\r\n" +
                        "  echo [ERROR] No Node.js project found under \"%%ROOT%%\"\r\n" +
                        "  pause\r\n" +
                        "  exit /b 1\r\n" +
                        ")\r\n" +
                        "echo Using Project Directory: \"%%PROJECT_DIR%%\"\r\n" +
                        "\r\n" +
                        "REM ===== Change to project directory =====\r\n" +
                        "cd /d \"%%PROJECT_DIR%%\"\r\n" +
                        "\r\n" +
                        "REM ===== Check if npm is installed =====\r\n" +
                        "where npm >nul 2>&1\r\n" +
                        "if errorlevel 1 (\r\n" +
                        "  echo [ERROR] npm not found in PATH.\r\n" +
                        "  echo Please install Node.js from: https://nodejs.org/\r\n" +
                        "  pause\r\n" +
                        "  exit /b 1\r\n" +
                        ")\r\n" +
                        "\r\n" +
                        "REM ===== Check if node_modules exists =====\r\n" +
                        "if not exist \"node_modules\" (\r\n" +
                        "  echo node_modules not found. Running npm install...\r\n" +
                        "  call npm install\r\n" +
                        "  if errorlevel 1 (\r\n" +
                        "    echo [ERROR] npm install FAILED!\r\n" +
                        "    pause\r\n" +
                        "    exit /b 1\r\n" +
                        "  )\r\n" +
                        ")\r\n" +
                        "\r\n" +
                        "REM ===== Check if sonar-scanner is installed =====\r\n" +
                        "where sonar-scanner >nul 2>&1\r\n" +
                        "if errorlevel 1 (\r\n" +
                        "  echo [ERROR] sonar-scanner not found in PATH.\r\n" +
                        "  echo Please install: npm install -g sonarqube-scanner\r\n" +
                        "  echo Or download from: https://docs.sonarqube.org/latest/analysis/scan/sonarscanner/\r\n" +
                        "  pause\r\n" +
                        "  exit /b 1\r\n" +
                        ")\r\n" +
                        "\r\n" +
                        "REM ===== Check if sonar-project.properties exists =====\r\n" +
                        "if not exist \"sonar-project.properties\" (\r\n" +
                        "  echo [ERROR] sonar-project.properties not found!\r\n" +
                        "  pause\r\n" +
                        "  exit /b 1\r\n" +
                        ")\r\n" +
                        "\r\n" +
                        "echo Starting Sonar Analysis for Node.js project...\r\n" +
                        "echo Using sonar-project.properties for configuration\r\n" +
                        "\r\n" +
                        "sonar-scanner\r\n" +
                        "\r\n" +
                        "if errorlevel 1 (\r\n" +
                        "  echo [ERROR] Sonar Analysis FAILED! Exit Code: %%errorlevel%%\r\n" +
                        "  pause\r\n" +
                        "  exit /b 1\r\n" +
                        ")\r\n" +
                        "echo Sonar Analysis COMPLETED successfully.\r\n" +
                        "pause\r\n" +
                        "exit /b 0\r\n",
                LocalDateTime.now()
        );

        Files.writeString(scriptPath, scriptContent);
        log.info("Created Sonar script for Node.js project at: {}", scriptPath);
        return scriptPath;
    }

    //หาไฟล์ pom

    // READ: get all
    public List<RepositoryModel> getAllRepository() {
        List<ProjectsEntity> projects = projectsRepository.findAll();
        List<RepositoryModel> repoModels = new ArrayList<>();
        for (ProjectsEntity e : projects) {
            RepositoryModel m = new RepositoryModel();
            m.setProjectId(e.getProjectId());
            m.setName(e.getName());
            m.setRepositoryUrl(e.getRepositoryUrl());
            m.setProjectType(e.getProjectType());
            repoModels.add(m);
        }
        return repoModels;
    }

    // READ: get by id
    public RepositoryModel getByIdDetail(UUID id) {
        ProjectsEntity project = projectsRepository.findById(id)
                .orElseThrow(ProjectNotFoundException::new);

        RepositoryModel model = new RepositoryModel();
        model.setProjectId(project.getProjectId());
        model.setName(project.getName());
        model.setRepositoryUrl(project.getRepositoryUrl());
        model.setProjectType(project.getProjectType());
        model.setCreatedAt(project.getCreatedAt());
        model.setUpdatedAt(project.getUpdatedAt());
        return model;
    }

    // UPDATE
    @Transactional
    public RepositoryModel updateRepository(UUID id, RepositoryModel repo) {
        ProjectsEntity project = projectsRepository.findById(id)
                .orElseThrow(ProjectNotFoundException::new);

        project.setName(repo.getName());
        project.setSonarProjectKey(repo.getSonarProjectKey());
        project.setProjectType(repo.getProjectType());
        project.setUpdatedAt(repo.getUpdatedAt());

        ProjectsEntity updated = projectsRepository.save(project);

        repo.setProjectId(updated.getProjectId());
        repo.setName(updated.getName());
        repo.setRepositoryUrl(updated.getRepositoryUrl());
        repo.setProjectType(updated.getProjectType());
        repo.setCreatedAt(updated.getCreatedAt());
        repo.setUpdatedAt(updated.getUpdatedAt());
        return repo;
    }

    // DELETE
    @Transactional
    public void deleteRepository(UUID id) {
        // ถ้าต้องการเช็กก่อนลบ:
        if (!projectsRepository.existsById(id)) {
            throw new ProjectNotFoundException();
        }
        projectsRepository.deleteById(id);
    }

    // ACTION: clone repo by project id
    public String cloneRepositoryByProjectId(UUID projectId) {
        Optional<String> repoUrlOptional = projectsRepository.findRepositoryUrlByProjectId(projectId);
        if (repoUrlOptional.isEmpty()) {
            throw new RepositoryUrlNotFoundForProjectException(projectId);
        }

        String repoUrl = repoUrlOptional.get();
        // ดึงชื่อ repo จาก URL เช่น https://github.com/user/my-app.git → my-app
        String repoName = repoUrl.substring(repoUrl.lastIndexOf("/") + 1).replace(".git", "");
        String targetDir = "C:/cloned-projects/" + repoName;

        File directory = new File(targetDir);
        if (directory.exists()) {
            throw new TargetDirectoryAlreadyExistsException(targetDir);
        }

        try {
            Git.cloneRepository()
                    .setURI(repoUrl)
                    .setDirectory(directory)
                    .call();
            return "Repository cloned successfully to " + targetDir;
        } catch (GitAPIException e) {
            // ให้ Global Handler แปลงเป็น JSON 500
            throw new GitCloneException(e.getMessage());
        }
    }
}
