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

    @PostMapping
    public ResponseEntity<RepositoryResponse> createRepository(@Valid @RequestBody RepositoryCreateRequest req,
                                                               UriComponentsBuilder uriBuilder) {
        RepositoryResponse created = repositoryService.createRepository(req);

        URI location = uriBuilder
                .path("/api/repositories/{id}")
                .buildAndExpand(created.projectId())
                .toUri();

        return ResponseEntity.created(location).body(created);
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

    @PostMapping("/clone")
    public String cloneRepo(@RequestParam UUID projectId) {
        return repositoryService.cloneRepositoryByProjectId(projectId);
    }
}
