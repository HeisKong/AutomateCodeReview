package com.automate.CodeReview.Service;

import com.automate.CodeReview.Models.NotiModel;
import com.automate.CodeReview.entity.NotiEntity;
import com.automate.CodeReview.entity.ProjectsEntity;
import com.automate.CodeReview.entity.ScansEntity;
import com.automate.CodeReview.repository.NotiRepository;
import com.automate.CodeReview.repository.ProjectsRepository;
import com.automate.CodeReview.repository.ScansRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class NotiService {

    private final NotiRepository notiRepository;
    private final ProjectsRepository projectsRepository;
    private final ScansRepository scanRepository;

    public NotiService(NotiRepository notiRepository, ProjectsRepository projectsRepository, ScansRepository scanRepository) {
        this.notiRepository = notiRepository;
        this.projectsRepository = projectsRepository;
        this.scanRepository = scanRepository;
    }
//Noti Create Repo
    public NotiModel createRepoNoti(UUID projectId, String message) {
        ProjectsEntity project = projectsRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));

        NotiEntity noti = new NotiEntity();
        noti.setNotiId(UUID.randomUUID());
        noti.setProject(project);
        noti.setTypeNoti("Repository");
        noti.setMessage(message);
        noti.setRead(false);

        NotiEntity saved = notiRepository.save(noti);

        NotiModel model = new NotiModel();
        model.setScanId(model.getScanId());
        model.setProjectId(model.getProjectId());
        model.setMessage(message);
        model.setRead(false);
        model.setTypeNoti("Repository");

        return model;


    }

    @Async
    @Transactional
    public void createRepoNotiAsync(UUID projectId, String message) {
        createRepoNoti(projectId, message);
    }
//Noti Create Repo
    public NotiModel markAsRead(UUID id){
        NotiEntity noti = notiRepository.findByNotiId(id)
                .orElseThrow(() -> new EntityNotFoundException("Noti not found with id: " + id));

        noti.setRead(true);
        NotiEntity saved = notiRepository.save(noti);
        NotiModel model = new NotiModel();
        model.setNotiId(saved.getNotiId());
        model.setRead(saved.getRead());
        model.setTypeNoti(saved.getTypeNoti());
        model.setMessage(saved.getMessage());
        model.setCreatedAt(saved.getCreatedAt());
        return model;
    }

    public NotiModel scanNoti(UUID scanId,UUID projectId, String message){
        if (scanId == null) throw new IllegalArgumentException("scan is required");
        NotiModel model = new NotiModel();
        model.setScanId(model.getScanId());
        model.setProjectId(model.getProjectId());
        model.setMessage(message);
        model.setRead(false);
        model.setTypeNoti("Scan");

        ProjectsEntity project = projectsRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));

        ScansEntity scan = scanRepository.findById(scanId)
                .orElseThrow(() -> new IllegalArgumentException("Scan not found"));

        NotiEntity noti = new NotiEntity();
        noti.setNotiId(UUID.randomUUID());
        noti.setProject(project);
        noti.setScan(scan);
        noti.setTypeNoti("scan");
        noti.setMessage(message);
        noti.setRead(false);

        NotiEntity saved = notiRepository.save(noti);

        return model;
    }
    @Async
    @Transactional
    public void scanNotiAsync(UUID scanId,UUID projectId, String message) {
        scanNoti(scanId,projectId, message);
    }

    public NotiModel exportReportNoti(UUID projectId, String message) {
        ProjectsEntity project = projectsRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));

        NotiEntity noti = new NotiEntity();
        noti.setNotiId(UUID.randomUUID());
        noti.setProject(project);
        noti.setTypeNoti("Export");
        noti.setMessage(message);
        noti.setRead(false);

        NotiEntity saved = notiRepository.save(noti);

        NotiModel model = new NotiModel();
        model.setScanId(model.getScanId());
        model.setProjectId(model.getProjectId());
        model.setMessage(message);
        model.setRead(false);
        model.setTypeNoti("Export");


        return model;
    }
    @Async
    @Transactional
    public void exportReportNotiAsync(UUID projectId, String message) {
        exportReportNoti(projectId, message);
    }


    //Delete
    public void deleteNoti (UUID id){
        if (!notiRepository.existsById(id)){
            throw new EntityNotFoundException("Noti not found with id: " + id);
        }
        notiRepository.deleteById(id);
    }

    //GetAll
    public List<NotiModel> getAllNotification() {
        List<NotiEntity> notiEntities = notiRepository.findAll();
        List<NotiModel> notiModels = new ArrayList<NotiModel>();
        for (NotiEntity notiEntity : notiEntities) {
            NotiModel notiModel = new NotiModel();
            notiModel.setNotiId(notiEntity.getNotiId());
            notiModel.setProjectId(notiEntity.getProject().getProjectId());
            notiModel.setScanId(notiEntity.getScan() != null ? notiEntity.getScan().getScanId() : null);
            notiModel.setRead(notiEntity.getRead());
            notiModel.setTypeNoti(notiEntity.getTypeNoti());
            notiModel.setMessage(notiEntity.getMessage());
            notiModel.setCreatedAt(notiEntity.getCreatedAt());
            notiModels.add(notiModel);
        }
        return notiModels;
    }
}
