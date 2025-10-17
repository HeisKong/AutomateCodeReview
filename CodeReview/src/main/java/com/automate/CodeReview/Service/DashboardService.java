package com.automate.CodeReview.Service;

import com.automate.CodeReview.Models.DashboardModel;
import com.automate.CodeReview.entity.ProjectsEntity;
import com.automate.CodeReview.entity.ScansEntity;
import com.automate.CodeReview.entity.UsersEntity;
import com.automate.CodeReview.repository.ProjectsRepository;
import com.automate.CodeReview.repository.ScansRepository;
import com.automate.CodeReview.repository.UsersRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
public class DashboardService {

    private final ScansRepository scansRepository;
    private final ProjectsRepository projectsRepository;
    private final UsersRepository usersRepository;
    private final ObjectMapper objectMapper;
    public DashboardService(ProjectsRepository projectsRepository,
                            ScansRepository scansRepository,
                            UsersRepository usersRepository,
                            ObjectMapper objectMapper) {
        this.projectsRepository = projectsRepository;
        this.scansRepository = scansRepository;
        this.usersRepository = usersRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<DashboardModel.DashboardDTO> getOverview(UUID userId) {
        UsersEntity user = usersRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        final boolean isAdmin = "ADMIN".equalsIgnoreCase(String.valueOf(user.getRole()));
        List<ProjectsEntity> projects = isAdmin
                ? projectsRepository.findAll()
                : projectsRepository.findByUser_UserId(userId);


        List<DashboardModel.DashboardDTO> dashboardList = new ArrayList<>(projects.size());
        for (ProjectsEntity project : projects) {
            ScansEntity latestScan = scansRepository
                    .findFirstByProject_ProjectIdOrderByStartedAtDesc(project.getProjectId())
                    .orElse(null);


//            for (ProjectsEntity project : projects) {
//                List<ScansEntity> scans = scansRepository.findByProject_ProjectId(project.getProjectId());
//                ScansEntity latestScan = scans.isEmpty() ? null : scans.get(0);
//
//                ObjectMapper objectMapper = new ObjectMapper();
//                for(ScansEntity scanEntity : scans){
//                    DashboardModel.DashboardDTO model = DashboardModel.DashboardDTO();
//                    model.setProjectId(scanEntity.getProject().getProjectId());
//                    mode
//                    try {
//                        String metricsJson = scanRepository.findMetricsByScanId(scanEntity.getScanId());
//                        if (metricsJson != null && !metricsJson.isBlank()) {
//                            Map<String, Object> metricsMap = objectMapper.readValue(metricsJson, Map.class);
//                            model.setMetrics(metricsMap);
//                        } else {
//                            model.setMetrics(new HashMap<>());
//                        }
//                    } catch (Exception e) {
//                        log.error("Failed to parse metrics JSON for scan {}: {}", scanEntity.getScanId(), e.getMessage());
//                        model.setMetrics(new HashMap<>());
//                    }
//
//
//                    scansModel.add(model);
//                }






                DashboardModel.DashboardDTO model = new DashboardModel.DashboardDTO();
            model.setProjectId(project.getProjectId());
            model.setProjectName(project.getName());
            model.setMetrics(latestScan != null ? (com.fasterxml.jackson.databind.JsonNode) latestScan.getMetrics() : null); // JsonNode → JSON ตรง ๆ

            dashboardList.add(model);
        }
        return dashboardList;
    }

    @Transactional(readOnly = true)
    public List<DashboardModel.HistoryDTO> getHistory(UUID userId) {
        UsersEntity user = usersRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        final boolean isAdmin = "ADMIN".equalsIgnoreCase(String.valueOf(user.getRole()));

        List<ProjectsEntity> projects = isAdmin
                ? projectsRepository.findAll()
                : projectsRepository.findByUser_UserId(userId);

        List<DashboardModel.HistoryDTO> historyList = new ArrayList<>();
        for (ProjectsEntity project : projects) {
            List<ScansEntity> scans = scansRepository.findByProject_ProjectId(project.getProjectId());
            ScansEntity latestScan = scans.isEmpty() ? null : scans.get(0);

            DashboardModel.HistoryDTO h = new DashboardModel.HistoryDTO();
            h.setProjectId(project.getProjectId());
            h.setProjectName(project.getName());
            h.setProjectType(project.getProjectType());
            if (latestScan != null) {
                h.setQualityGate(latestScan.getQualityGate());
            }
            h.setCreatedAt(latestScan != null ? latestScan.getStartedAt() : null);

            historyList.add(h);
        }
        return historyList;
    }

    @Transactional(readOnly = true)
    public List<DashboardModel.TrendsDTO> getTrends(UUID userId) {
        UsersEntity user = usersRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        final boolean isAdmin = "ADMIN".equalsIgnoreCase(String.valueOf(user.getRole()));

        List<ProjectsEntity> projects = isAdmin
                ? projectsRepository.findAll()
                : projectsRepository.findByUser_UserId(userId);
        List<DashboardModel.TrendsDTO> trendsList = new ArrayList<>();

        for (ProjectsEntity project : projects) {
            List<ScansEntity> scans = scansRepository.findByProject_ProjectId(project.getProjectId());
            for (ScansEntity scan : scans) {

                    DashboardModel.TrendsDTO t = new DashboardModel.TrendsDTO();
                    t.setId(scan.getScanId());
                    t.setStartTime(scan.getStartedAt());
                    t.setQualityGate(scan.getQualityGate());
                    t.setReliabilityGate(scan.getReliabilityGate());
                    t.setSecurityGate(scan.getSecurityGate());
                    t.setMaintainabilityGate(scan.getMaintainabilityGate());
                    t.setSecurityReviewGate(scan.getSecurityReviewGate());
                    trendsList.add(t);
                }
            }
        return trendsList;
    }
}
