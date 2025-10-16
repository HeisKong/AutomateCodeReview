package com.automate.CodeReview.Service;

import com.automate.CodeReview.Models.AssignModel;
import com.automate.CodeReview.entity.AssignHistoryEntity;
import com.automate.CodeReview.entity.IssuesEntity;
import com.automate.CodeReview.entity.UsersEntity;
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

    public AssignHistoryService(UsersRepository usersRepository, IssuesRepository issuesRepository,  AssignHistoryRepository assignHistoryRepository) {
        this.usersRepository = usersRepository;
        this.issuesRepository = issuesRepository;
        this.assignHistoryRepository = assignHistoryRepository;
    }

    public List<AssignModel.getAssign>  getAssignHistory(UUID userId) {
        UsersEntity user = usersRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        List<IssuesEntity> issue = issuesRepository.findIssuesEntity_ByAssignedTo(user);

        List<AssignModel.getAssign> assignModels = new ArrayList<>();



        for(IssuesEntity issueEntity : issue){
            List<AssignHistoryEntity> assignHistory = assignHistoryRepository.findByAssignedTo(userId);
            String annotation = null;
            if(!assignHistory.isEmpty()){
                annotation = assignHistory.getFirst().getStatus();
            }
            AssignModel.getAssign assignModel = new AssignModel.getAssign();
            assignModel.setAssignedTo(userId);
            assignModel.setIssueId(issueEntity.getIssuesId());
            assignModel.setMessage(issueEntity.getMessage());
            assignModel.setSeverity(issueEntity.getSeverity());
            assignModel.setStatus(issueEntity.getStatus());
            assignModel.setAnnotation(annotation);
            assignModel.setDueDate(issueEntity.getDueDate());

            assignModels.add(assignModel);
        }
        return assignModels;
    }


    public List<AssignModel.setAssign> setAssignHistory(UUID userId, String status, String annotation) {
        UsersEntity user = usersRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        List<IssuesEntity> issue = issuesRepository.findIssuesEntity_ByAssignedTo(user);
        List<AssignModel.setAssign> assignModels = new ArrayList<>();

        for(IssuesEntity issueEntity : issue){
            AssignModel.setAssign assignModel = new AssignModel.setAssign();
            assignModel.setAssignedTo(userId);
            assignModel.setIssueId(issueEntity.getIssuesId());
            assignModel.setMessage(issueEntity.getMessage());
            assignModel.setSeverity(issueEntity.getSeverity());
            assignModel.setStatus(status);
            assignModel.setAnnotation(annotation);
            assignModel.setDueDate(issueEntity.getDueDate());

            assignModels.add(assignModel);
        }
        return assignModels;
    }
}
