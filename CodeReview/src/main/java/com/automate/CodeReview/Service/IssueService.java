package com.automate.CodeReview.Service;

import com.automate.CodeReview.Models.AssignModel;
import com.automate.CodeReview.Models.CommentModel;
import com.automate.CodeReview.Models.IssueModel;
import com.automate.CodeReview.entity.*;
import com.automate.CodeReview.exception.IssueNotFoundException;
import com.automate.CodeReview.exception.UserNotFoundException;
import com.automate.CodeReview.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.*;

@Slf4j
@Service
public class IssueService {

    private final IssuesRepository issuesRepository;
    private final UsersRepository usersRepository;
    private final CommentsRepository commentsRepository;
    private final AssignHistoryRepository assignHistoryRepository;


    public IssueService(IssuesRepository issuesRepository, UsersRepository usersRepository, CommentsRepository commentsRepository,  AssignHistoryRepository assignHistoryRepository) {
        this.issuesRepository = issuesRepository;
        this.usersRepository = usersRepository;
        this.commentsRepository = commentsRepository;
        this.assignHistoryRepository = assignHistoryRepository;
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
            IssueModel model = getIssueModel(issue);
            saveIssue(issueList, issue, model);

        }
        return issueList;
    }

    private static IssueModel getIssueModel(IssuesEntity issue) {
        IssueModel model  = new IssueModel();
        model.setIssueId(issue.getIssuesId());

        UUID scanId = (issue.getScan() != null) ? issue.getScan().getScanId() : null;
        model.setScanId(scanId);

        UUID projectId = null;
        String projectName = null;
        if (issue.getScan() != null && issue.getScan().getProject() != null) {
            projectId = issue.getScan().getProject().getProjectId();
            projectName = issue.getScan().getProject().getName();
        }
        model.setProjectId(projectId);
        model.setProjectName(projectName);
        model.setScanId(issue.getScan().getScanId());
        return model;
    }

    private void saveIssue(List<IssueModel> issueList, IssuesEntity issue, IssueModel model) {
        model.setIssueKey(issue.getIssueKey());
        model.setType(issue.getType());
        model.setComponent(issue.getComponent());
        model.setMessage(issue.getMessage());
        model.setSeverity(issue.getSeverity());
        model.setAssignedTo(
                issue.getAssignedTo() != null ? issue.getAssignedTo().getUserId() : null
        );
        model.setStatus(issue.getStatus());
        model.setCreatedAt(String.valueOf(issue.getCreatedAt()));

        issueList.add(model);
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
            saveIssue(issueList, issue, model);

        }
        return issueList;
    }

    public IssueModel getIssueById(UUID issueId) {
        IssuesEntity issue = issuesRepository.findById(issueId)
                .orElseThrow(IssueNotFoundException::new);

        IssueModel model = new IssueModel();


        UUID projectId =null;
        String projectName = null;
        if (issue.getScan() != null && issue.getScan().getProject() != null) {
            projectId = issue.getScan().getProject().getProjectId();
            projectName = issue.getScan().getProject().getName();
        }

        UUID assignedTo = (issue.getAssignedTo() != null)
                ? issue.getAssignedTo().getUserId()
                : null;

        model.setIssueId(issue.getIssuesId());
        model.setProjectId(projectId);
        model.setProjectName(projectName);
        model.setScanId(issue.getScan().getScanId());
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
    public AssignModel.getAssign assign(UUID issueId, UUID assignTo, LocalDate dueDate) {

        IssuesEntity issue = issuesRepository.findById(issueId)
                .orElseThrow(IssueNotFoundException::new);

        UsersEntity user = usersRepository.findById(assignTo)
                .orElseThrow(UserNotFoundException::new);

        if ("DONE".equalsIgnoreCase(String.valueOf(issue.getStatus()))) {
            throw new IllegalStateException("This issue is DONE and cannot be reassigned.");
        }
        if ("IN PROGRESS".equalsIgnoreCase(String.valueOf(issue.getStatus()))) {
            throw new IllegalStateException("This issue is reassigned.");
        }
        boolean alreadyRecorded = assignHistoryRepository
                .existsByIssues_IssuesIdAndAssignedTo(issueId, assignTo);
        if (alreadyRecorded || issue.getStatus().equals("REJECT")) {
            issue.setAssignedTo(user);
            return saveAssign(assignTo, issue, dueDate, user);
        }

        if (!issue.getStatus().equals("DONE") || issue.getAssignedTo().equals(user) ){
            issue.setAssignedTo(user);
            issue.setStatus("IN PROGRESS");
            issue.setDueDate(dueDate);
            saveAssign(assignTo, issue, dueDate, user);
        }

        return saveAssign(assignTo, issue, dueDate, user);

    }

    private AssignModel.getAssign saveAssign(UUID assignTo, IssuesEntity issue, LocalDate dueDate,  UsersEntity user) {
        issuesRepository.save(issue);
        AssignHistoryEntity assign = new AssignHistoryEntity();
        AssignModel.getAssign model =  new AssignModel.getAssign();
        if(!Objects.equals(assign.getMessage(), issue.getMessage())) {
            assign.setIssues(issue);
            assign.setAssignedTo(assignTo);
            assign.setStatus("IN PROGRESS");
            assign.setMessage(issue.getMessage());
            assign.setDueDate(dueDate);
            assignHistoryRepository.save(assign);

            model.setIssueId(issue.getIssuesId());
            model.setAssignedTo(assignTo);
            model.setSeverity(issue.getSeverity());
            model.setAssignedToName(user.getUsername());
            model.setStatus(issue.getStatus());
            model.setMessage(issue.getMessage());
            model.setDueDate(dueDate);


        }
        return model;
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




