package com.automate.CodeReview.Service;

import com.automate.CodeReview.Models.CommentModel;
import com.automate.CodeReview.Models.IssueModel;
import com.automate.CodeReview.entity.CommentsEntity;
import com.automate.CodeReview.entity.IssuesEntity;
import com.automate.CodeReview.entity.ProjectsEntity;
import com.automate.CodeReview.entity.UsersEntity;
import com.automate.CodeReview.exception.IssueNotFoundException;
import com.automate.CodeReview.exception.NoIssuesFoundException;
import com.automate.CodeReview.exception.UserNotFoundException;
import com.automate.CodeReview.repository.CommentsRepository;
import com.automate.CodeReview.repository.IssuesRepository;
import com.automate.CodeReview.repository.UsersRepository;
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

    public IssueService(IssuesRepository issuesRepository, UsersRepository usersRepository, CommentsRepository commentsRepository) {
        this.issuesRepository = issuesRepository;
        this.usersRepository = usersRepository;
        this.commentsRepository = commentsRepository;
    }

    @Transactional(readOnly = true)
    public List<IssueModel> getAllIssue(UUID userId) {
        UsersEntity user = usersRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        final boolean isAdmin = "ADMIN".equalsIgnoreCase(String.valueOf(user.getRole()));

        List<IssuesEntity> issues = isAdmin
                ? issuesRepository.findAll()
                : issuesRepository.findIssuesEntity_ByAssignedTo(user);

        if (issues.isEmpty()) {
            throw new NoIssuesFoundException();
        }

        List<IssueModel> result = new ArrayList<>(issues.size());
        for (IssuesEntity issue : issues) {
            result.add(new IssueModel(
                    issue.getIssuesId(),
                    issue.getScan() != null ? issue.getScan().getScanId() : null,
                    issue.getIssueKey(),
                    issue.getType(),
                    issue.getSeverity(),
                    issue.getComponent(),
                    issue.getMessage(),
                    issue.getAssignedTo() != null ? String.valueOf(issue.getAssignedTo().getUserId()) : null,
                    issue.getStatus(),
                    issue.getCreatedAt() != null ? issue.getCreatedAt().toString() : null
            ));
        }
        return result;
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
