package com.automate.CodeReview.Service;

import com.automate.CodeReview.Models.RepositoryModel;
import com.automate.CodeReview.entity.ProjectsEntity;
import com.automate.CodeReview.entity.UsersEntity;
import com.automate.CodeReview.exception.*;
import com.automate.CodeReview.repository.ProjectsRepository;
import com.automate.CodeReview.repository.UsersRepository;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class RepositoryService {

    private final ProjectsRepository projectsRepository;
    private final UsersRepository usersRepository;

    public RepositoryService(ProjectsRepository projectsRepository, UsersRepository usersRepository) {
        this.projectsRepository = projectsRepository;
        this.usersRepository = usersRepository;
    }

    // CREATE
    @Transactional
    public RepositoryModel createRepository(RepositoryModel repo) {
        if (repo.getUser() == null) {
            throw new IllegalArgumentException("userId is required");
        }
        if (repo.getProjectId() == null) {
            repo.setProjectId(UUID.randomUUID());
        }

        UUID userId = repo.getUser();
        UsersEntity user = usersRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        ProjectsEntity project = new ProjectsEntity();
        project.setProjectId(repo.getProjectId());
        project.setUser(user);
        project.setName(repo.getName());
        project.setRepositoryUrl(repo.getRepositoryUrl());
        project.setSonarProjectKey(repo.getSonarProjectKey());
        project.setProjectType(repo.getProjectType());
        project.setCreatedAt(repo.getCreatedAt());
        project.setUpdatedAt(repo.getUpdatedAt());

        ProjectsEntity saved = projectsRepository.save(project);

        // map back to model
        repo.setProjectId(saved.getProjectId());
        repo.setName(saved.getName());
        repo.setRepositoryUrl(saved.getRepositoryUrl());
        repo.setSonarProjectKey(saved.getSonarProjectKey());
        repo.setProjectType(saved.getProjectType());
        repo.setCreatedAt(saved.getCreatedAt());
        repo.setUpdatedAt(saved.getUpdatedAt());
        return repo;
    }

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
