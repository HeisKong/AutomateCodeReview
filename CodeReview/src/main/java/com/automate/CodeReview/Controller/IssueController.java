package com.automate.CodeReview.Controller;


import com.automate.CodeReview.Service.IssueService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
@RequestMapping("/api/issues")
public class IssueController {

    private final IssueService issueService;

    public IssueController(IssueService issueService) {
        this.issueService = issueService;
    }

    @GetMapping
    public List<IssueModel> getAllIssue(){
        return IssueService.getAllIssue();
    }

    @GetMapping("/{id}")
    public ResponseEntity<IssueModel> getIssueById(@PathVariable UUID id){
        IssueModel issue = issueService.getIssueById(id);
        if(issue != null){
            return ResponseEntity.ok(issue);
        }else {
            return ResponseEntity.notFound().build();
        }
    }

    //เว้นไว้ก่อนยังไม่รู้จะใช้อะไร
    @PutMapping("/{id}/assign")
    public ResponseEntity<IssueModel> assign(@PathVariable UUID id,@RequestParam String assignTo){
        return ResponseEntity.ok(issueService.assign(id, assignTo));
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<Map<String, Object>> addComment(@PathVariable UUID id,@RequestBody String message){
        return ResponseEntity.ok(issueService.addComment(id, message));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<IssueModel> updateStatus(@PathVariable UUID id, @RequestParam String status){
        return ResponseEntity.ok(issueService.updateStatus(id, status));
    }

}