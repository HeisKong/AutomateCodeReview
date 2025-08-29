package com.automate.CodeReview.Service;

import com.automate.CodeReview.Models.DashboardModel;
import com.automate.CodeReview.Models.HistoryModel;
import com.automate.CodeReview.Models.TrendsModel;
import com.automate.CodeReview.entity.GradeEntity;
import com.automate.CodeReview.entity.ProjectsEntity;
import com.automate.CodeReview.entity.ScansEntity;
import com.automate.CodeReview.repository.GradeRepository;
import com.automate.CodeReview.repository.ProjectsRepository;
import com.automate.CodeReview.repository.ScansRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class DashboardService {

    private final ScansRepository scansRepository;
    private final ProjectsRepository projectsRepository;
    private final GradeRepository gradeRepository;
    public DashboardService(ProjectsRepository projectsRepository, ScansRepository scansRepository, GradeRepository gradeRepository) {
        this.projectsRepository = projectsRepository;
        this.scansRepository = scansRepository;
        this.gradeRepository = gradeRepository;
    }

    public List<DashboardModel> getOverview(UUID userId) {
        List<ProjectsEntity> projects = projectsRepository.findByUser_UserId(userId);
        if (projects.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Projects not found for user");
        }

        List<DashboardModel> dashboardList = new ArrayList<>();
        for (ProjectsEntity project : projects) {
            List<ScansEntity> scans = scansRepository.findByProject_ProjectId(project.getProjectId());
            ScansEntity latestScan = scans.isEmpty() ? null : scans.get(0);

            DashboardModel model = new DashboardModel();
            model.setId(userId);
            model.setName(project.getName());
            model.setMetrics(latestScan != null && latestScan.getMetrics() != null ? latestScan.getMetrics() : "0");
            dashboardList.add(model);
        }

        return dashboardList;
    }



    public List<HistoryModel> getHistory(UUID userId) {
        List<ProjectsEntity> projects = projectsRepository.findByUser_UserId(userId);
        if (projects.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Projects not found for user");
        }

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


    public List<TrendsModel> getTrends(UUID userId) {
        List<ProjectsEntity> projects = projectsRepository.findByUser_UserId(userId);
        if (projects.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Projects not found for user");
        }

        List<TrendsModel> trendsList = new ArrayList<>();

        for (ProjectsEntity project : projects) {
            List<ScansEntity> scanOpt = scansRepository.findByProject_ProjectId(project.getProjectId());

            List<ScansEntity> scans = scansRepository.findByProject_ProjectId(project.getProjectId());
            for (ScansEntity scan : scans) {
                List<GradeEntity> gateHistories = gradeRepository.findByScan_ScanId(scan.getScanId());
                for (GradeEntity gate : gateHistories) {
                    TrendsModel model = new TrendsModel();
                    model.setId(scan.getScanId());
                    model.setStartTime(scan.getStartedAt());
                    model.setQualityGate(gate.getQualityGate());
                    trendsList.add(model);
                }
            }
        }

        return trendsList;
    }


}

