package com.automate.CodeReview.Service;

import com.automate.CodeReview.Models.RepositoryModel;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class RepositoryService {

    //create
    public RepositoryModel createRepository(RepositoryModel repo){
        return repo;
    }

    //getAll
    public List<RepositoryModel> getAllRepository() {
        return null;
    }

    //getById
    public RepositoryModel getByIdDetail(UUID id){
        return null;
    }

    //update
    public RepositoryModel updateRepository(UUID id, RepositoryModel repo){
        return null;
    }

    //delete
    public void deleteRepository(UUID id) {
        return;
    }

    //clone ทำพร้อม update เดียวค่อยไปแก้

}
