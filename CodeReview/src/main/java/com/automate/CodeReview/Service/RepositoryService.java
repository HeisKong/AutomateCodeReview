package com.automate.CodeReview.Service;

import com.automate.CodeReview.Models.RepositoryModel;
import com.automate.CodeReview.entity.ProjectsEntity;
import com.automate.CodeReview.entity.UsersEntity;
import com.automate.CodeReview.repository.ProjectsRepository;
import com.automate.CodeReview.repository.UsersRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class RepositoryService {

    private final ProjectsRepository projectsRepository;
    private final UsersRepository usersRepository;

    public RepositoryService(ProjectsRepository projectsRepository, UsersRepository usersRepository) {
        this.projectsRepository = projectsRepository;
        this.usersRepository = usersRepository;
    }

    //create
    public RepositoryModel createRepository(RepositoryModel repo){
        if (repo.getId() == null) {
            repo.setId(UUID.randomUUID());
        }

        //เดี๋ยวกลับมาแก้ ยังงงๆอยู่ อันนี้ก็ใช้ได้
        List<UUID> userIds = repo.getUserIds() != null ? repo.getUserIds() : List.of();
        List<UsersEntity> user = usersRepository.findAllById(userIds);

        ProjectsEntity project = new ProjectsEntity();
        project.setId(repo.getId());
        project.setUserId(user);
        project.setName(repo.getName());
        project.setRepositoryUrl(repo.getRepositoryUrl());
        project.setSonarProjectKey(repo.getSonarProjectKey());
        project.setProjectType(repo.getProjectType());
        project.setCreatedAt(repo.getCreatedAt());
        project.setUpdatedAt(repo.getUpdatedAt());

        ProjectsEntity saveProject = projectsRepository.save(project);
        repo.setId(saveProject.getId());
        repo.setName(saveProject.getName());
        repo.setRepositoryUrl(saveProject.getRepositoryUrl());
        repo.setSonarProjectKey(saveProject.getSonarProjectKey());
        repo.setProjectType(saveProject.getProjectType());
        repo.setCreatedAt(saveProject.getCreatedAt());
        repo.setUpdatedAt(saveProject.getUpdatedAt());

        return repo;


    }


    //getAll
    public List<RepositoryModel> getAllRepository() {
        List<ProjectsEntity> project = projectsRepository.findAll();
        List<RepositoryModel> repoModels = new ArrayList<>();

        for (ProjectsEntity projectsEntity : project) {
            RepositoryModel model = new RepositoryModel();
            model.setId(projectsEntity.getId());
            model.setName(projectsEntity.getName());
            model.setRepositoryUrl(projectsEntity.getRepositoryUrl());
            model.setProjectType(projectsEntity.getProjectType());
            repoModels.add(model);
        }
        return repoModels;
    }

    //getById
    public RepositoryModel getByIdDetail(UUID id){
        ProjectsEntity project = projectsRepository.findById(id).orElseThrow(() -> new RuntimeException("Project not found"));
        RepositoryModel model = new RepositoryModel();
        model.setId(project.getId());
        model.setName(project.getName());
        model.setRepositoryUrl(project.getRepositoryUrl());
        model.setProjectType(project.getProjectType());
        model.setCreatedAt(project.getCreatedAt());
        model.setUpdatedAt(project.getUpdatedAt());
        return model;
    }

    //update
    public RepositoryModel updateRepository(UUID id, RepositoryModel repo){
        ProjectsEntity project = projectsRepository.findById(id).orElseThrow(() -> new RuntimeException("Project not found"));

        project.setName(repo.getName());
        project.setSonarProjectKey(repo.getSonarProjectKey());
        project.setProjectType(repo.getProjectType());
        project.setUpdatedAt(repo.getUpdatedAt());

        ProjectsEntity updatedProject = projectsRepository.save(project);
        repo.setId(updatedProject.getId());
        repo.setName(updatedProject.getName());
        repo.setRepositoryUrl(updatedProject.getRepositoryUrl());
        repo.setProjectType(updatedProject.getProjectType());
        repo.setCreatedAt(updatedProject.getCreatedAt());
        repo.setUpdatedAt(updatedProject.getUpdatedAt());
        return repo;
    }

    //delete
    public void deleteRepository(UUID id) {
        projectsRepository.deleteById(id);
    }

    //clone ทำพร้อม update เดียวค่อยไปแก้
    public RepositoryModel cloneRepository(UUID id){
        return null;
    }

}
