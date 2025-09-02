package com.automate.CodeReview.Service;

import com.automate.CodeReview.entity.IssuesEntity;
import com.automate.CodeReview.entity.ProjectsEntity;
import com.automate.CodeReview.entity.ScansEntity;
import com.automate.CodeReview.repository.IssuesRepository;
import com.automate.CodeReview.repository.ProjectsRepository;
import com.automate.CodeReview.repository.ScansRepository;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class SonarSyncService {

    private final SonarClient sonarClient;
    private final ProjectsRepository projectRepo;
    private final ScansRepository scanRepo;
    private final IssuesRepository issueRepo;

    public void syncAllProjects() {
        var sonarProjects = sonarClient.fetchProjects(500);

        for (var sp : sonarProjects) {
            String key = sp.get("key");
            String name = sp.get("name");

            // upsert project
            ProjectsEntity project = projectRepo.findBySonarProjectKey(key)
                    .orElseGet(() -> {
                        ProjectsEntity p = new ProjectsEntity();
                        p.setProjectId(UUID.randomUUID());
                        p.setSonarProjectKey(key);
                        p.setCreatedAt(LocalDateTime.now());
                        return p;
                    });

            project.setName(name);
            project.setUpdatedAt(OffsetDateTime.now().toLocalDateTime());
            projectRepo.save(project);

            // measures -> สร้างแถวใน scans
            JsonNode measures = sonarClient.fetchMeasures(key);
            String qg = extractAlertStatus(measures); // OK/ERROR/WARN
            ScansEntity scan = new ScansEntity();
            scan.setScanId(UUID.randomUUID());
            scan.setProject(project);
            scan.setStatus("SUCCESS");
            scan.setStartedAt(OffsetDateTime.now().toLocalDateTime());   // ใส่เวลาจริงตามที่คุณต้องการ
            scan.setCompletedAt(OffsetDateTime.now().toLocalDateTime());
            scan.setQualityGate(qg);
            scan.setMetrics(measures.toString());      // ถ้าแมป JSONB เป็น String
            scanRepo.save(scan);

            // issues
            var issues = sonarClient.fetchIssues(key);
            for (JsonNode i : issues) {
                IssuesEntity is = new IssuesEntity();
                is.setIssuesId(UUID.randomUUID());
                is.setScan(scan);
                is.setIssueKey(i.path("key").asText());
                is.setType(i.path("type").asText());
                is.setSeverity(i.path("severity").asText());
                is.setComponent(i.path("component").asText());
                is.setMessage(i.path("message").asText());
                is.setStatus(i.path("status").asText());
                is.setCreatedAt(parseIso(i.path("creationDate").asText()).toLocalDateTime());
                issueRepo.save(is);
            }
        }
    }

    private String extractAlertStatus(JsonNode measuresRoot) {
        // รูปแบบ JSON ของ /measures/component:
        // { "component":{ "measures":[{"metric":"alert_status","value":"OK"}, ...] } }
        for (JsonNode m : measuresRoot.path("component").path("measures")) {
            if ("alert_status".equals(m.path("metric").asText())) {
                return m.path("value").asText(null);
            }
        }
        return null;
    }

    private OffsetDateTime parseIso(String s) {
        if (s == null || s.isBlank()) return null;
        return OffsetDateTime.parse(s);
    }
}

