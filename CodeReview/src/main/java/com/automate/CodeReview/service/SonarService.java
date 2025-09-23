package com.automate.CodeReview.service;

import com.automate.CodeReview.Config.SonarProperties;
import com.automate.CodeReview.dto.SonarBatchResponse;
import com.automate.CodeReview.dto.SonarSummary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SonarService {

    @Autowired
    @Qualifier("sonarWebClient")
    private WebClient sonarClient;
    @Autowired private SonarProperties props;

    private static final List<String> METRICS = List.of(
            "security_rating",
            "reliability_rating",
            "maintainability_rating", // ถ้าไม่มีจะ fallback ไป sqale_rating ข้างล่าง
            "coverage",
            "duplicated_lines_density",
            "bugs",
            "vulnerabilities",
            "code_smells"
    );

    private String mapGrade(String n) {
        return switch (n) {
            case "1" -> "A";
            case "2" -> "B";
            case "3" -> "C";
            case "4" -> "D";
            case "5" -> "E";
            default -> null;
        };
    }

    private Retry retryPolicy() {
        // retry แบบ backoff คงที่ง่าย ๆ; ปรับได้ตามต้องการ
        return Retry.fixedDelay(props.getMaxRetries(), Duration.ofMillis(800))
                .filter(ex -> true);
    }

    public Mono<SonarSummary> getSummary(String projectKey) {
        MultiValueMap<String, String> q = new LinkedMultiValueMap<>();
        q.add("component", projectKey);
        q.add("metricKeys", String.join(",", METRICS));

        return sonarClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/measures/component").queryParams(q).build())
                .retrieve()
                .bodyToMono(Map.class)
                .retryWhen(retryPolicy())
                .map(json -> {
                    Map component = (Map) json.get("component");
                    List<Map<String, Object>> measures = (List<Map<String, Object>>) component.get("measures");

                    Map<String, String> m = new HashMap<>();
                    for (var it : measures) {
                        m.put((String) it.get("metric"), String.valueOf(it.get("value")));
                    }

                    // fallback: บางเวอร์ชันใช้ sqale_rating
                    if (!m.containsKey("maintainability_rating") && m.containsKey("sqale_rating")) {
                        m.put("maintainability_rating", m.get("sqale_rating"));
                    }

                    Map<String, String> grades = Map.of(
                            "security", mapGrade(m.get("security_rating")),
                            "reliability", mapGrade(m.get("reliability_rating")),
                            "maintainability", mapGrade(m.get("maintainability_rating"))
                    );

                    Map<String, String> metrics = new HashMap<>();
                    metrics.put("coverage", m.getOrDefault("coverage", null));
                    metrics.put("duplication", m.getOrDefault("duplicated_lines_density", null));
                    metrics.put("bugs", m.getOrDefault("bugs", null));
                    metrics.put("vulnerabilities", m.getOrDefault("vulnerabilities", null));
                    metrics.put("codeSmells", m.getOrDefault("code_smells", null));

                    return new SonarSummary(projectKey, grades, metrics);
                });
    }

    public Mono<List<SonarSummary>> getSummaries(List<String> projectKeys) {
        int concurrency = Math.min(8, Math.max(1, projectKeys.size()));
        return Flux.fromIterable(projectKeys)
                .flatMap(pk -> getSummary(pk).onErrorResume(ex -> Mono.empty()), concurrency)
                .collectList();
    }

    public Mono<List<String>> fetchAllProjectKeys(int pageSize, int maxPages) {
        int ps = Math.min(Math.max(pageSize, 1), 500); // ps <= 500
        return Flux.range(1, maxPages)
                .concatMap(page -> sonarClient.get()
                        .uri(uriBuilder -> uriBuilder.path("/api/components/search")
                                .queryParam("qualifiers", "TRK")
                                .queryParam("p", page)
                                .queryParam("ps", ps)
                                .build())
                        .retrieve()
                        .bodyToMono(Map.class)
                        .retryWhen(retryPolicy())
                        .map(json -> {
                            var comps = (List<Map<String, Object>>) json.getOrDefault("components", List.of());
                            return comps.stream().map(c -> (String) c.get("key")).toList();
                        })
                )
                .takeUntil(list -> list.isEmpty())
                .flatMapIterable(list -> list)
                .distinct()
                .collectList();
    }

    public Mono<List<SonarSummary>> getAllProjectSummaries(int pageSize, int maxPages) {
        return fetchAllProjectKeys(pageSize, maxPages).flatMap(this::getSummaries);
    }

    /* ===== CSV helper ===== */
    public String toCsv(List<SonarSummary> items) {
        // header
        List<String> headers = new ArrayList<>(List.of(
                "projectKey",
                "securityGrade","reliabilityGrade","maintainabilityGrade",
                "coverage","duplication","bugs","vulnerabilities","codeSmells"
        ));
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", headers)).append("\n");

        for (var it : items) {
            var g = it.grades();
            var m = it.metrics();
            List<String> row = List.of(
                    safe(it.projectKey()),
                    safe(g.get("security")),
                    safe(g.get("reliability")),
                    safe(g.get("maintainability")),
                    safe(m.get("coverage")),
                    safe(m.get("duplication")),
                    safe(m.get("bugs")),
                    safe(m.get("vulnerabilities")),
                    safe(m.get("codeSmells"))
            );
            sb.append(row.stream().map(this::csv).collect(Collectors.joining(","))).append("\n");
        }
        return sb.toString();
    }

    private String csv(String v) {
        if (v == null) return "";
        // wrap ถ้ามีคอมมา/เครื่องหมายคำพูด
        if (v.contains(",") || v.contains("\"")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    private String safe(Object v) { return v == null ? "" : v.toString(); }
}

