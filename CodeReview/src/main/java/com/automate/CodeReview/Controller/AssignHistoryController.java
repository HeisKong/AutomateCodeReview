package com.automate.CodeReview.Controller;

import com.automate.CodeReview.Models.AssignModel;
import com.automate.CodeReview.Service.AssignHistoryService;
import com.automate.CodeReview.dto.request.UpdateStatusRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(("api/assign"))
public class AssignHistoryController {

    private final AssignHistoryService assignHistoryService;

    public AssignHistoryController(AssignHistoryService assignHistoryService) {
        this.assignHistoryService = assignHistoryService;
    }

    @GetMapping("/{userId}")
    public List<AssignModel.getAssign> getAssignHistory(@PathVariable UUID userId){
        return assignHistoryService.getAssignHistory(userId);
    }

    @PutMapping("/update/{userId}/{issueId}")
    public AssignModel.setAssign updateStatus(@PathVariable UUID userId,
                                              @PathVariable UUID issueId,
                                              @RequestBody UpdateStatusRequest body) {
        return assignHistoryService.updateStatus(userId, issueId, body.getStatus(), body.getAnnotation());
    }
}
