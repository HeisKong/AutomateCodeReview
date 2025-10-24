package com.automate.CodeReview.Controller;

import com.automate.CodeReview.dto.SonarBatchResponse;
import com.automate.CodeReview.dto.SonarSummary;
import com.automate.CodeReview.Service.SonarService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/sonar")
public class SonarController {

    @Autowired private SonarService sonarService;

    /* ------- Single Project ------- */
    @GetMapping(value = "/{projectKey}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<SonarSummary> summary(@PathVariable String projectKey) {
        return sonarService.getSummary(projectKey);
    }

    /* ------- Batch by CSV query ------- */
    // GET /api/sonar/batch?projectKeys=a,b,c
    @GetMapping(value = "/batch", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<SonarBatchResponse> batchByQuery(@RequestParam("projectKeys") String keysCsv) {
        var keys = Arrays.stream(keysCsv.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();
        return sonarService.getSummaries(keys)
                .map(items -> new SonarBatchResponse(items, Map.of("requested", keys.size(), "returned", items.size())));
    }

    /* ------- Batch by POST body ------- */
    // POST /api/sonar/batch { "projectKeys": ["a","b","c"] }
    @PostMapping(value = "/batch", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<SonarBatchResponse> batchByBody(@RequestBody Map<String, Object> body) {
        var list = (List<?>) body.getOrDefault("projectKeys", List.of());
        var keys = list.stream().map(Object::toString).map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();
        return sonarService.getSummaries(keys)
                .map(items -> new SonarBatchResponse(items, Map.of("requested", keys.size(), "returned", items.size())));
    }

    /* ------- All projects (optional) ------- */
    // GET /api/sonar/batch/all?ps=300&pages=3
    @GetMapping(value = "/batch/all", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<SonarBatchResponse> batchAll(
            @RequestParam(name = "ps", defaultValue = "200") int pageSize,
            @RequestParam(name = "pages", defaultValue = "5") int maxPages
    ) {
        return sonarService.getAllProjectSummaries(pageSize, maxPages)
                .map(items -> new SonarBatchResponse(items, Map.of(
                        "returned", items.size(), "pageSize", pageSize, "maxPages", maxPages
                )));
    }
    /* ------- CSV export ------- */
    // GET /api/sonar/batch.csv?projectKeys=a,b,c
    @GetMapping(value = "/batch.csv", produces = "text/csv")
    public Mono<ResponseEntity<byte[]>> batchCsv(@RequestParam("projectKeys") String keysCsv) {
        var keys = Arrays.stream(keysCsv.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();
        return sonarService.getSummaries(keys)
                .map(items -> {
                    String csv = sonarService.toCsv(items);
                    return ResponseEntity.ok()
                            .header("Content-Disposition", "attachment; filename=\"sonar-metrics.csv\"")
                            .contentType(MediaType.parseMediaType("text/csv"))
                            .body(csv.getBytes(StandardCharsets.UTF_8));
                });
    }

    // GET /api/sonar/batch.all.csv?ps=300&pages=3
    @GetMapping(value = "/batch.all.csv", produces = "text/csv")
    public Mono<ResponseEntity<byte[]>> batchAllCsv(
            @RequestParam(name = "ps", defaultValue = "200") int pageSize,
            @RequestParam(name = "pages", defaultValue = "5") int maxPages
    ) {
        return sonarService.getAllProjectSummaries(pageSize, maxPages)
                .map(items -> {
                    String csv = sonarService.toCsv(items);
                    return ResponseEntity.ok()
                            .header("Content-Disposition", "attachment; filename=\"sonar-metrics-all.csv\"")
                            .contentType(MediaType.parseMediaType("text/csv"))
                            .body(csv.getBytes(StandardCharsets.UTF_8));
                });
    }
}
