package com.automate.CodeReview.Service;

import com.automate.CodeReview.Models.DashboardModel;
import com.automate.CodeReview.Models.HistoryModel;
import com.automate.CodeReview.Models.TrendsModel;
import com.automate.CodeReview.entity.ProjectsEntity;
import com.automate.CodeReview.entity.ScansEntity;
import com.automate.CodeReview.repository.ProjectsRepository;
import com.automate.CodeReview.repository.ScansRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class DashboardService {

    private final ScansRepository scansRepository;
    ProjectsRepository projectsRepository;
    public DashboardService(ProjectsRepository projectsRepository, ScansRepository scansRepository) {
        this.projectsRepository = projectsRepository;
        this.scansRepository = scansRepository;
    }

    public DashboardModel getOverview(UUID id){

        ProjectsEntity projectsEntity = projectsRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Project not found"));
        ScansEntity scansEntity = scansRepository.findById(projectsEntity.getProjectId())
                .orElseThrow(() -> new RuntimeException("Metrics not found"));
        DashboardModel dashboardModel = new DashboardModel();
        dashboardModel.setName(projectsEntity.getName());
        dashboardModel.setMetrics(String.valueOf(scansEntity.getMetrics()));
        return dashboardModel;
    }

    public List<HistoryModel> getHistory(UUID id){
        ProjectsEntity projectsEntity = projectsRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Project not found"));
        HistoryModel historyModel = new HistoryModel();
        historyModel.setName(projectsEntity.getName());
        historyModel.setCreatedAt(projectsEntity.getCreatedAt());
        return List.of(historyModel);
    }

    public TrendsModel getTrends(UUID id){
        return new TrendsModel();
    }
}

