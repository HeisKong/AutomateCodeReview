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
        if (repo.getProjectId() == null) {
            repo.setProjectId(UUID.randomUUID());
        }
        //เดี๋ยวกลับมาแก้ ยังงงๆอยู่ อันนี้ก็ใช้ได้
        UUID userId = repo.getUser();
        UsersEntity user = usersRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        ProjectsEntity project = new ProjectsEntity();
        project.setProjectId(repo.getProjectId());
        project.setUser(user);
        project.setName(repo.getName());
        project.setRepositoryUrl(repo.getRepositoryUrl());
        project.setSonarProjectKey(repo.getSonarProjectKey());
        project.setProjectType(repo.getProjectType());
        project.setCreatedAt(repo.getCreatedAt());
        project.setUpdatedAt(repo.getUpdatedAt());

        ProjectsEntity saveProject = projectsRepository.save(project);
        repo.setProjectId(saveProject.getProjectId());
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
            model.setProjectId(projectsEntity.getProjectId());
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
        model.setProjectId(project.getProjectId());
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
        repo.setProjectId(updatedProject.getProjectId());
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

   /* //clone ทำพร้อม update เดียวค่อยไปแก้
    public RepositoryModel cloneRepository(UUID id){
        return null;
    }*/

}
