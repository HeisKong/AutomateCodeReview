package com.automate.CodeReview.Service;


import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class IssueService {

    public List<IssueModel> getAllIssue(){
        return null;
    }

    public IssueModel getIssueById (UUID id){
        return null;
    }

    public IssueModel assign(UUID id, String assignTo){
        return null;
    }

    public Map<String, Object> addComment(UUID id, String massage){
        return null;
    }

    public IssueModel updateStatus(UUID id, String status){
        return null;
    }
}
