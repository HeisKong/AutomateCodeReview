package com.automate.CodeReview.Controller;

import com.automate.CodeReview.Models.RepositoryModel;
import com.automate.CodeReview.Service.RepositoryService;
import com.automate.CodeReview.dto.RepositoryCreateRequest;
import com.automate.CodeReview.dto.RepositoryResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/repositories")
public class RepositoryController {

    private final RepositoryService repositoryService;

    public RepositoryController(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    @PostMapping("/add")
    public ResponseEntity<RepositoryResponse> createRepository(@Valid @RequestBody RepositoryCreateRequest req,
                                                               UriComponentsBuilder uriBuilder) {
        RepositoryResponse created = repositoryService.createRepository(req);

        URI location = uriBuilder
                .path("/api/repositories/{id}")
                .buildAndExpand(created.projectId())
                .toUri();

        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/getAll/{userId}")
    public List<RepositoryModel> getAllRepository(@PathVariable UUID userId) {
        return repositoryService.getAllRepository(userId);
    }

    @GetMapping("/detail/{projectId}")
    public ResponseEntity<RepositoryModel> getByIdDetail(@PathVariable UUID projectId) {
        RepositoryModel repo = repositoryService.getByIdDetail(projectId);
        if (repo != null) {
            return ResponseEntity.ok(repo);
        }else{
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/{projectId}")
    public ResponseEntity<RepositoryModel> updateRepository(@PathVariable UUID projectId,
                                                            @RequestBody RepositoryModel repo){
        return ResponseEntity.ok(repositoryService.updateRepository(projectId, repo));
    }

    @DeleteMapping("/{projectId}")
    public ResponseEntity<Void> deleteRepository(@PathVariable UUID projectId) {
        repositoryService.deleteRepository(projectId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/clone")
    public String cloneRepo(@RequestParam UUID projectId) {
        return repositoryService.cloneRepositoryByProjectId(projectId);
    }
}
