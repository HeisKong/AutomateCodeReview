package com.automate.CodeReview.Service;

import com.automate.CodeReview.Models.CommentModel;
import com.automate.CodeReview.Models.IssueModel;
import com.automate.CodeReview.entity.CommentsEntity;
import com.automate.CodeReview.entity.IssuesEntity;
import com.automate.CodeReview.entity.UsersEntity;
import com.automate.CodeReview.repository.CommentsRepository;
import com.automate.CodeReview.repository.IssuesRepository;
import com.automate.CodeReview.repository.UsersRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class IssueService {

    private final IssuesRepository issuesRepository;
    private final UsersRepository usersRepository;
    private final CommentsRepository commentsRepository;

    public IssueService(IssuesRepository issuesRepository, UsersRepository usersRepository, CommentsRepository commentsRepository) {
        this.issuesRepository = issuesRepository;
        this.usersRepository = usersRepository;
        this.commentsRepository = commentsRepository;
    }

    public List<IssueModel> getAllIssue(UUID userId) {
        List<IssuesEntity> issues = issuesRepository.findByScan_Project_User_UserId(userId);
        if (issues.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No issues found for user");
        }

        List<IssueModel> result = new ArrayList<>();
        for (IssuesEntity issue : issues) {
            IssueModel model = new IssueModel(
                    issue.getIssuesId(),
                    issue.getScan().getScanId(),
                    issue.getIssueKey(),
                    issue.getType(),
                    issue.getSeverity(),
                    issue.getComponent(),
                    issue.getMessage(),
                    issue.getAssignedTo() != null ? issue.getAssignedTo().getUserId().toString() : null,
                    issue.getStatus(),
                    issue.getCreatedAt().toString()
            );
            result.add(model);
        }

        return result;
    }

    public IssueModel assign(UUID issueId, String assignToUserId) {
        IssuesEntity issue = issuesRepository.findById(issueId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Issue not found"));

        UsersEntity user = usersRepository.findById(UUID.fromString(assignToUserId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        issue.setAssignedTo(user);
        return getIssueById(issue.getIssuesId());
    }

    public IssueModel getIssueById(UUID issueId) {
        IssuesEntity issue = issuesRepository.findById(issueId)
                .orElseThrow(() -> new RuntimeException("Issue not found"));

        return new IssueModel(
                issue.getIssuesId(),
                issue.getScan().getScanId(),
                issue.getIssueKey(),
                issue.getType(),
                issue.getSeverity(),
                issue.getComponent(),
                issue.getMessage(),
                issue.getAssignedTo() != null ? issue.getAssignedTo().getUserId().toString() : null,
                issue.getStatus(),
                issue.getCreatedAt().toString()
        );
    }

    public IssueModel updateStatus(UUID issueId, String status) {
        IssuesEntity issue = issuesRepository.findById(issueId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Issue not found"));

        issue.setStatus(status);
        return getIssueById(issue.getIssuesId());
    }

    public CommentModel addComment(UUID issueId, String comment, UUID userId) {
        IssuesEntity issue = issuesRepository.findById(issueId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Issue not found"));
        UsersEntity user = usersRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

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
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Issue not found"));

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
