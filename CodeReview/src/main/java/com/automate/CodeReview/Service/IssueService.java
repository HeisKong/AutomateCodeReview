package com.automate.CodeReview.Service;

import com.automate.CodeReview.Models.CommentModel;
import com.automate.CodeReview.Models.IssueModel;
import com.automate.CodeReview.entity.*;
import com.automate.CodeReview.exception.IssueNotFoundException;
import com.automate.CodeReview.exception.UserNotFoundException;
import com.automate.CodeReview.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class IssueService {

    private final IssuesRepository issuesRepository;
    private final UsersRepository usersRepository;
    private final CommentsRepository commentsRepository;
    private final ProjectsRepository projectsRepository;


    public IssueService(IssuesRepository issuesRepository, UsersRepository usersRepository, CommentsRepository commentsRepository, ProjectsRepository projectsRepository) {
        this.issuesRepository = issuesRepository;
        this.usersRepository = usersRepository;
        this.commentsRepository = commentsRepository;
        this.projectsRepository = projectsRepository;
    }

    @Transactional(readOnly = true)
    public List<IssueModel> getAllIssue(UUID userId) {
        UsersEntity user = usersRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        final boolean isAdmin = "ADMIN".equalsIgnoreCase(String.valueOf(user.getRole()));

        List<IssuesEntity> issues = isAdmin
                ? issuesRepository.findAll()
                : issuesRepository.findByScan_Project_User_UserId(userId);

        List<IssueModel> issueList = new ArrayList<>();
        for (IssuesEntity issue : issues) {
            IssueModel model  = new IssueModel();
            model.setIssueId(issue.getIssuesId());

            UUID scanId = (issue.getScan() != null) ? issue.getScan().getScanId() : null;
            model.setScanId(scanId);

            UUID projectId = null;
            if (issue.getScan() != null && issue.getScan().getProject() != null) {
                projectId = issue.getScan().getProject().getProjectId();
            }
            model.setProjectId(projectId);
            model.setScanId(issue.getScan().getScanId());
            model.setIssueKey(issue.getIssueKey());
            model.setType(issue.getType());
            model.setComponent(issue.getComponent());
            model.setMessage(issue.getMessage());
            model.setSeverity(issue.getSeverity());
            model.setAssignedTo(String.valueOf(issue.getAssignedTo()));
            model.setStatus(issue.getStatus());
            model.setCreatedAt(String.valueOf(issue.getCreatedAt()));

            issueList.add(model);

        }
        return issueList;
    }


    @Transactional(readOnly = true)
    public List<IssueModel> getIssueByProject(UUID projectId) {


        List<IssuesEntity> issues = issuesRepository.findByScan_Project_ProjectId(projectId);

        List<IssueModel> issueList = new ArrayList<>();
        for (IssuesEntity issue : issues) {
            IssueModel model  = new IssueModel();
            model.setIssueId(issue.getIssuesId());

            UUID scanId = (issue.getScan() != null) ? issue.getScan().getScanId() : null;
            model.setScanId(scanId);

            if (issue.getScan() != null && issue.getScan().getProject() != null) {
                projectId = issue.getScan().getProject().getProjectId();
            }
            model.setProjectId(projectId);
            model.setScanId(issue.getScan().getScanId());
            model.setIssueKey(issue.getIssueKey());
            model.setType(issue.getType());
            model.setComponent(issue.getComponent());
            model.setMessage(issue.getMessage());
            model.setSeverity(issue.getSeverity());
            model.setAssignedTo(String.valueOf(issue.getAssignedTo()));
            model.setStatus(issue.getStatus());
            model.setCreatedAt(String.valueOf(issue.getCreatedAt()));

            issueList.add(model);

        }
        return issueList;
    }



    @Transactional
    public IssueModel assign(UUID issueId, String assignToUserId) {
        IssuesEntity issue = issuesRepository.findById(issueId)
                .orElseThrow(IssueNotFoundException::new);

        UUID assigneeUuid = UUID.fromString(assignToUserId);
        UsersEntity user = usersRepository.findById(assigneeUuid)
                .orElseThrow(UserNotFoundException::new);

        issue.setAssignedTo(user);
        issuesRepository.save(issue);
        return getIssueById(issue.getIssuesId());
    }

    public IssueModel getIssueById(UUID issueId) {
        IssuesEntity issue = issuesRepository.findById(issueId)
                .orElseThrow(IssueNotFoundException::new);

        IssueModel model = new IssueModel();

        // assignedTo -> ส่งเป็น userId (string) หรือเปลี่ยน type ใน IssueModel เป็น UUID ก็ได้
        String assignedTo = (issue.getAssignedTo() != null)
                ? issue.getAssignedTo().getUserId().toString()
                : null;

        model.setIssueId(issue.getIssuesId());
        // ไม่ set projectId
        model.setScanId(issue.getScan().getScanId());
        model.setIssueKey(issue.getIssueKey());
        model.setType(issue.getType());
        model.setComponent(issue.getComponent());
        model.setMessage(issue.getMessage());
        model.setSeverity(issue.getSeverity());
        model.setAssignedTo(assignedTo);
        model.setStatus(issue.getStatus());
        model.setCreatedAt(issue.getCreatedAt() != null ? issue.getCreatedAt().toString() : null);

        return model;
    }

    @Transactional
    public IssueModel updateStatus(UUID issueId, String status) {
        IssuesEntity issue = issuesRepository.findById(issueId)
                .orElseThrow(IssueNotFoundException::new);

        issue.setStatus(status);
        issuesRepository.save(issue);
        return getIssueById(issue.getIssuesId());
    }

    @Transactional
    public CommentModel addComment(UUID issueId, String comment, UUID userId) {
        IssuesEntity issue = issuesRepository.findById(issueId)
                .orElseThrow(IssueNotFoundException::new);
        UsersEntity user = usersRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        CommentsEntity entity = new CommentsEntity();
        entity.setIssues(issue);
        entity.setUser(user);
        entity.setComment(comment);

        CommentsEntity saved = commentsRepository.save(entity);

        CommentModel model = new CommentModel();
        model.setComment(saved.getComment());
        model.setCreatedAt(saved.getCreatedAt());
        model.setUserId(saved.getUser().getUserId());
        model.setIssueId(saved.getIssues().getIssuesId());
        return model;
    }

    public List<CommentModel> getCommentsByIssue(UUID issueId) {
        IssuesEntity issue = issuesRepository.findById(issueId)
                .orElseThrow(IssueNotFoundException::new);

        List<CommentsEntity> comments = commentsRepository.findByIssues(issue);

        return comments.stream().map(entity -> {
            CommentModel model = new CommentModel();
            model.setIssueId(issueId);
            model.setUserId(entity.getUser().getUserId());
            model.setComment(entity.getComment());
            model.setCreatedAt(entity.getCreatedAt());
            return model;
        }).toList();
    }
}
