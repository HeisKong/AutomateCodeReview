package com.automate.CodeReview.Service;

import com.automate.CodeReview.Models.AssignModel;
import com.automate.CodeReview.entity.AssignHistoryEntity;
import com.automate.CodeReview.entity.IssuesEntity;
import com.automate.CodeReview.entity.UsersEntity;
import com.automate.CodeReview.exception.IssueNotFoundException;
import com.automate.CodeReview.repository.AssignHistoryRepository;
import com.automate.CodeReview.repository.IssuesRepository;
import com.automate.CodeReview.repository.UsersRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AssignHistoryService {

    private final IssuesRepository issuesRepository;
    private final UsersRepository usersRepository;
    private final AssignHistoryRepository assignHistoryRepository;

    public AssignHistoryService(UsersRepository usersRepository,
                                AssignHistoryRepository assignHistoryRepository,
                                IssuesRepository issuesRepository
                                ) {
        this.usersRepository = usersRepository;
        this.assignHistoryRepository = assignHistoryRepository;
        this.issuesRepository = issuesRepository;
    }

    public List<AssignModel.getAssign> getAssignHistory(UUID userId) {
        UsersEntity user = usersRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        List<AssignHistoryEntity> assignHist = assignHistoryRepository.findByAssignedTo(user.getUserId());
        List<AssignModel.getAssign> result = new ArrayList<>();

        for (AssignHistoryEntity a : assignHist) {
            AssignModel.getAssign dto = getGetAssign(a, user);
            result.add(dto);
        }
        return result;
    }

    private static AssignModel.getAssign getGetAssign(AssignHistoryEntity a, UsersEntity user) {
        AssignModel.getAssign dto = new AssignModel.getAssign();

        IssuesEntity issue = a.getIssues();
        dto.setIssueId(issue != null ? issue.getIssuesId() : null);
        dto.setSeverity(issue != null ? issue.getSeverity() : null);

        dto.setMessage(a.getMessage());

        dto.setAssignedTo(a.getAssignedTo());
        dto.setAssignedToName(user.getUsername());
        dto.setStatus(a.getStatus());
        dto.setAnnotation(a.getAnnotation());
        dto.setDueDate(a.getDueDate());
        return dto;
    }


    public AssignModel.setAssign updateStatus(UUID userId, UUID issueId, String rawStatus, String annotation) {
        UsersEntity user = usersRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        IssuesEntity issue = issuesRepository.findById(issueId)
                .orElseThrow(IssueNotFoundException::new);

        String statusUpper = rawStatus == null ? "" : rawStatus.trim().toUpperCase();

        UUID assignedTo = (issue.getAssignedTo() != null) ? user.getUserId() : null;

        if ("REJECT".equals(statusUpper) && !"DONE".equals(issue.getStatus())) {
            issue.setStatus("OPEN");
            issuesRepository.save(issue);

            AssignHistoryEntity hist = assignHistoryRepository
                    .findByIssues_IssuesIdAndStatus(issueId, "PENDING")
                    .orElseGet(AssignHistoryEntity::new);

            hist.setIssues(issue);
            hist.setStatus("REJECT");
            hist.setAssignedTo(assignedTo);
            hist.setAnnotation(annotation);
            assignHistoryRepository.save(hist);
        }

        if ("DONE".equals(statusUpper)) {
            issue.setStatus(statusUpper);
            issuesRepository.save(issue);

            AssignHistoryEntity hist = assignHistoryRepository
                    .findByIssues_IssuesIdAndStatus(issueId, "IN PROGRESS")
                    .orElseGet(AssignHistoryEntity::new);

            hist.setIssues(issue);
            hist.setStatus("DONE");
            hist.setAssignedTo(assignedTo);
            hist.setAnnotation(annotation);
            hist.setDueDate(issue.getDueDate());
            assignHistoryRepository.save(hist);
        }

        if ("ACCEPT".equals(statusUpper)) {
            issue.setStatus("IN PROGRESS");
            issuesRepository.save(issue);

            AssignHistoryEntity hist = assignHistoryRepository
                    .findByIssues_IssuesIdAndStatus(issueId, "PENDING")
                    .orElseGet(AssignHistoryEntity::new);

            hist.setIssues(issue);
            hist.setStatus("IN PROGRESS");
            hist.setAssignedTo(assignedTo);
            hist.setAnnotation(annotation);
            hist.setDueDate(issue.getDueDate());
            assignHistoryRepository.save(hist);
        }

        AssignModel.setAssign dto = new AssignModel.setAssign();
        dto.setAssignedTo(assignedTo);
        dto.setIssueId(issue.getIssuesId());
        dto.setSeverity(issue.getSeverity());
        dto.setMessage(issue.getMessage());
        dto.setStatus(issue.getStatus());
        dto.setAnnotation(annotation);
        dto.setDueDate(issue.getDueDate());
        return dto;
    }

}
