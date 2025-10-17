package com.automate.CodeReview.Controller;

import com.automate.CodeReview.Models.CommentModel;
import com.automate.CodeReview.Models.IssueModel;
import com.automate.CodeReview.Service.IssueService;
import com.automate.CodeReview.dto.CommentDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@CrossOrigin(origins = "http://localhost:4200")
@RequestMapping("/api/issues")
public class IssueController {

    private final IssueService issueService;

    public IssueController(IssueService issueService) {
        this.issueService = issueService;
    }

    @GetMapping("/user/{userId}")
    public List<IssueModel> getAllIssue(@PathVariable UUID userId) {
        return issueService.getAllIssue(userId);
    }

    @GetMapping("/project/{projectId}")
    public List<IssueModel> getIssueByProject(@PathVariable UUID projectId) {
        return issueService.getIssueByProject(projectId);
    }


    @GetMapping("/{issueId}")
    public ResponseEntity<IssueModel> getIssueById(@PathVariable UUID issueId){
        IssueModel issue = issueService.getIssueById(issueId);
        if(issue != null){
            return ResponseEntity.ok(issue);
        }
        return null;
    }

    @PutMapping("/{id}/assign")
    public ResponseEntity<IssueModel> assign(@PathVariable UUID id,@RequestParam String assignTo){
        return ResponseEntity.ok(issueService.assign(id, assignTo));
    }


    @PostMapping("/{issueId}/comments")
    public ResponseEntity<CommentModel> addComment(@PathVariable UUID issueId, @RequestBody CommentDTO message, @RequestParam UUID userId){
        return ResponseEntity.ok(issueService.addComment(issueId, message.getComment(), userId));
    }

    @GetMapping("/{issueId}/comments")
    public ResponseEntity<List<CommentModel>> getCommentsByIssue(@PathVariable UUID issueId) {
        return ResponseEntity.ok(issueService.getCommentsByIssue(issueId));
    }


    @PutMapping("/{issueId}/status")
    public ResponseEntity<IssueModel> updateStatus(@PathVariable UUID issueId, @RequestParam String status){
        return ResponseEntity.ok(issueService.updateStatus(issueId, status));
    }

}