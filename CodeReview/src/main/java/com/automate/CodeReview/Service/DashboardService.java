package com.automate.CodeReview.Service;

import com.automate.CodeReview.Models.DashboardModel;
import com.automate.CodeReview.dto.IssueDTO;
import com.automate.CodeReview.entity.GradeEntity;
import com.automate.CodeReview.entity.ProjectsEntity;
import com.automate.CodeReview.entity.ScansEntity;
import com.automate.CodeReview.entity.UsersEntity;
<<<<<<< Updated upstream
=======
//import com.automate.CodeReview.exception.ProjectsNotFoundForUserException;
>>>>>>> Stashed changes
import com.automate.CodeReview.repository.GradeRepository;
import com.automate.CodeReview.repository.ProjectsRepository;
import com.automate.CodeReview.repository.ScansRepository;
import com.automate.CodeReview.repository.UsersRepository;
<<<<<<< Updated upstream
import com.fasterxml.jackson.databind.ObjectMapper;
=======
>>>>>>> Stashed changes
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
public class DashboardService {

    private final ScansRepository scansRepository;
    private final ProjectsRepository projectsRepository;
    private final GradeRepository gradeRepository;
    private final UsersRepository usersRepository;
<<<<<<< Updated upstream
    private final ObjectMapper objectMapper;
    public DashboardService(ProjectsRepository projectsRepository,
                            ScansRepository scansRepository,
                            GradeRepository gradeRepository,
                            UsersRepository usersRepository,
                            ObjectMapper objectMapper) {
=======

    public DashboardService(ProjectsRepository projectsRepository,
                            ScansRepository scansRepository,
                            GradeRepository gradeRepository,
                            UsersRepository usersRepository) {
>>>>>>> Stashed changes
        this.projectsRepository = projectsRepository;
        this.scansRepository = scansRepository;
        this.gradeRepository = gradeRepository;
        this.usersRepository = usersRepository;
<<<<<<< Updated upstream
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<DashboardModel.DashboardDTO> getOverview(UUID userId) {
=======
    }

    @Transactional(readOnly = true)
    public List<DashboardModel> getOverview(UUID userId) {
>>>>>>> Stashed changes
        UsersEntity user = usersRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        final boolean isAdmin = "ADMIN".equalsIgnoreCase(String.valueOf(user.getRole()));
<<<<<<< Updated upstream
=======

        // แอดมินเห็นทั้งหมด, ผู้ใช้ทั่วไปเห็นของตัวเอง
>>>>>>> Stashed changes
        List<ProjectsEntity> projects = isAdmin
                ? projectsRepository.findAll()
                : projectsRepository.findByUser_UserId(userId);

<<<<<<< Updated upstream

        List<DashboardModel.DashboardDTO> dashboardList = new ArrayList<>(projects.size());
=======
//        if (projects.isEmpty()) {
//            throw new ProjectsNotFoundForUserException();
//        }

        List<DashboardModel> dashboardList = new ArrayList<>(projects.size());
>>>>>>> Stashed changes
        for (ProjectsEntity project : projects) {
            ScansEntity latestScan = scansRepository
                    .findFirstByProject_ProjectIdOrderByStartedAtDesc(project.getProjectId())
                    .orElse(null);
<<<<<<< Updated upstream


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

=======

            DashboardModel model = new DashboardModel();
            // model.setId(project.getProjectId());
            model.setId(userId);
            model.setName(project.getName());
//            model.setMetrics(
//                    latestScan != null && latestScan.getMetrics() != null
//                            ? String.valueOf(latestScan.getMetrics())
//                            : "0"
//            );
>>>>>>> Stashed changes
            dashboardList.add(model);
        }
        return dashboardList;
    }

    @Transactional(readOnly = true)
<<<<<<< Updated upstream
    public List<DashboardModel.HistoryDTO> getHistory(UUID userId) {
        UsersEntity user = usersRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
=======
    public List<HistoryModel> getHistory(UUID userId) {
        UsersEntity user = usersRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        final boolean isAdmin = "ADMIN".equalsIgnoreCase(String.valueOf(user.getRole()));


        List<ProjectsEntity> projects = isAdmin
                ? projectsRepository.findAll()
                : projectsRepository.findByUser_UserId(userId);
//        if (projects.isEmpty()) {
//            throw new ProjectsNotFoundForUserException();
//        }
>>>>>>> Stashed changes

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
<<<<<<< Updated upstream
    public List<DashboardModel.TrendsDTO> getTrends(UUID userId) {
        UsersEntity user = usersRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
=======
    public List<TrendsModel> getTrends(UUID userId) {
        UsersEntity user = usersRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        final boolean isAdmin = "ADMIN".equalsIgnoreCase(String.valueOf(user.getRole()));


        // แอดมินเห็นทั้งหมด, ผู้ใช้ทั่วไปเห็นของตัวเอง
        List<ProjectsEntity> projects = isAdmin
                ? projectsRepository.findAll()
                : projectsRepository.findByUser_UserId(userId);
//        if (projects.isEmpty()) {
//            throw new ProjectsNotFoundForUserException();
//        }
>>>>>>> Stashed changes

        final boolean isAdmin = "ADMIN".equalsIgnoreCase(String.valueOf(user.getRole()));

        List<ProjectsEntity> projects = isAdmin
                ? projectsRepository.findAll()
                : projectsRepository.findByUser_UserId(userId);
        List<DashboardModel.TrendsDTO> trendsList = new ArrayList<>();

        for (ProjectsEntity project : projects) {
            List<ScansEntity> scans = scansRepository.findByProject_ProjectId(project.getProjectId());
            for (ScansEntity scan : scans) {
                List<GradeEntity> gateHistories = gradeRepository.findByScan_ScanId(scan.getScanId());
                for (GradeEntity gate : gateHistories) {
                    DashboardModel.TrendsDTO t = new DashboardModel.TrendsDTO();
                    t.setId(scan.getScanId());
                    t.setStartTime(scan.getStartedAt());


                    t.setQualityGate(gate.getQualityGate());

                    t.setReliabilityGate(gate.getReliabilityGate());
                    t.setSecurityGate(gate.getSecurityGate());
                    t.setMaintainabilityGate(gate.getMaintainabilityGate());
                    t.setSecurityReviewGate(gate.getSecurityReviewGate());

                    trendsList.add(t);
                }
            }
        }
        return trendsList;
    }
}
