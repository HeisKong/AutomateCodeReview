package com.automate.CodeReview.Service;

import com.automate.CodeReview.Models.DashboardModel;
import com.automate.CodeReview.Models.HistoryModel;
import com.automate.CodeReview.Models.TrendsModel;
import com.automate.CodeReview.entity.GradeEntity;
import com.automate.CodeReview.entity.ProjectsEntity;
import com.automate.CodeReview.entity.ScansEntity;
import com.automate.CodeReview.entity.UsersEntity;
//import com.automate.CodeReview.exception.ProjectsNotFoundForUserException;
import com.automate.CodeReview.repository.GradeRepository;
import com.automate.CodeReview.repository.ProjectsRepository;
import com.automate.CodeReview.repository.ScansRepository;
import com.automate.CodeReview.repository.UsersRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class DashboardService {

    private final ScansRepository scansRepository;
    private final ProjectsRepository projectsRepository;
    private final GradeRepository gradeRepository;
    private final UsersRepository usersRepository;

    public DashboardService(ProjectsRepository projectsRepository,
                            ScansRepository scansRepository,
                            GradeRepository gradeRepository,
                            UsersRepository usersRepository) {
        this.projectsRepository = projectsRepository;
        this.scansRepository = scansRepository;
        this.gradeRepository = gradeRepository;
        this.usersRepository = usersRepository;
    }

    @Transactional(readOnly = true)
    public List<DashboardModel> getOverview(UUID userId) {
        UsersEntity user = usersRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        final boolean isAdmin = "ADMIN".equalsIgnoreCase(String.valueOf(user.getRole()));

        List<ProjectsEntity> projects = isAdmin
                ? projectsRepository.findAll()
                : projectsRepository.findByUser_UserId(userId);

//        if (projects.isEmpty()) {
//            throw new ProjectsNotFoundForUserException();
//        }

        List<DashboardModel> dashboardList = new ArrayList<>(projects.size());
        for (ProjectsEntity project : projects) {
            ScansEntity latestScan = scansRepository
                    .findFirstByProject_ProjectIdOrderByStartedAtDesc(project.getProjectId())
                    .orElse(null);

            DashboardModel model = new DashboardModel();
            model.setProjectId(project.getProjectId());
            model.setProjectName(project.getName());
            model.setMetrics(
                    latestScan != null && latestScan.getMetrics() != null
                            ? String.valueOf(latestScan.getMetrics())
                            : "0"
            );
            dashboardList.add(model);
        }
        return dashboardList;
    }

    @Transactional(readOnly = true)
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

        List<HistoryModel> historyList = new ArrayList<>();
        for (ProjectsEntity project : projects) {
            List<ScansEntity> scans = scansRepository.findByProject_ProjectId(project.getProjectId());
            ScansEntity latestScan = scans.isEmpty() ? null : scans.get(0);

            HistoryModel model = new HistoryModel();
            model.setId(project.getProjectId());
            model.setName(project.getName());
            model.setCreatedAt(latestScan != null ? latestScan.getStartedAt() : null);

            historyList.add(model);
        }
        return historyList;
    }

    @Transactional(readOnly = true)
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

        List<TrendsModel> trendsList = new ArrayList<>();

        for (ProjectsEntity project : projects) {
            List<ScansEntity> scans = scansRepository.findByProject_ProjectId(project.getProjectId());
            for (ScansEntity scan : scans) {
                List<GradeEntity> gateHistories = gradeRepository.findByScan_ScanId(scan.getScanId());
                for (GradeEntity gate : gateHistories) {
                    TrendsModel model = new TrendsModel();
                    model.setId(scan.getScanId());
                    model.setStartTime(scan.getStartedAt());
                    model.setQualityGate(gate.getQualityGate());
                    model.setReliabilityGate(gate.getReliabilityGate());
                    model.setSecurityGate(gate.getSecurityGate());
                    model.setMaintainabilityGate(gate.getMaintainabilityGate());
                    model.setSecurityReviewGate(gate.getSecurityReviewGate());

                    trendsList.add(model);
                }
            }
        }
        return trendsList;
    }
}
