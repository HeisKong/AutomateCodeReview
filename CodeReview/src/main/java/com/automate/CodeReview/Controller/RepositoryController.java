package com.automate.CodeReview.Controller;

import com.automate.CodeReview.Models.RepositoryModel;
import com.automate.CodeReview.Service.RepositoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/repositories")
public class RepositoryController {

    private final RepositoryService repositoryService;

    public RepositoryController(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    @PostMapping
    public ResponseEntity<RepositoryModel> createRepository(@RequestBody RepositoryModel repo) {
        return ResponseEntity.ok(repositoryService.createRepository(repo));
    }

    @GetMapping
    public List<RepositoryModel> getAllRepository(){
        return repositoryService.getAllRepository();
    }

    @GetMapping("/{id}")
    public ResponseEntity<RepositoryModel> getByIdDetail(@PathVariable UUID id) {
        RepositoryModel repo = repositoryService.getByIdDetail(id);
        if (repo != null) {
            return ResponseEntity.ok(repo);
        }else{
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<RepositoryModel> updateRepository(@PathVariable UUID id,
                                                            @RequestBody RepositoryModel repo){
        return ResponseEntity.ok(repositoryService.updateRepository(id, repo));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRepository(@PathVariable UUID id) {
        repositoryService.deleteRepository(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/clone")
    public ResponseEntity<Map<String, String>> cloneRepository(@PathVariable UUID id) {
        String path = repositoryService.cloneRepository(id);
        return ResponseEntity.ok(Map.of("clonePath", path));
    }
}
