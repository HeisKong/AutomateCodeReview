package com.automate.CodeReview.Controller;

import com.automate.CodeReview.Models.AssignModel;
import com.automate.CodeReview.Service.AssignHistoryService;
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

    @PutMapping("/update/{userId}")
    public List<AssignModel.setAssign> setAssignHistory(@PathVariable UUID userId, @RequestBody String status, @RequestBody String annotation) {
        return assignHistoryService.setAssignHistory(userId, status, annotation);
    }
}
