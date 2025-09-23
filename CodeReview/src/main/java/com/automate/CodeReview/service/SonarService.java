package com.automate.CodeReview.service;

import com.automate.CodeReview.Config.SonarProperties;
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
import org.springframework.beans.factory.annotation.Qualifier;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SonarService {

    @Autowired
    @Qualifier("sonarWebClient")
    private WebClient sonarClient;

    @Autowired
    private SonarProperties props;

    /** metric หลักที่ต้องการดึง */
    private static final List<String> METRICS = List.of(
            "security_rating",
            "reliability_rating",
            "software_quality_maintainability_rating",
            "sqale_rating",
            "coverage",
            "duplicated_lines_density",
            "bugs",
            "vulnerabilities",
            "code_smells"
    );

    /** ลำดับความสำคัญในการเลือก maintainability rating ที่มีจริง */
    private static final List<String> MAINTAIN_KEYS = List.of(
            "software_quality_maintainability_rating",
            "sqale_rating"
    );

    private String mapGrade(Object raw) {
        if (raw == null) return "N/A";
        String val = raw.toString().trim();

        // handle float values like "1.0"
        if (val.endsWith(".0")) {
            val = val.substring(0, val.length() - 2); // convert "1.0" -> "1"
        }

        return switch (val) {
            case "1" -> "A";
            case "2" -> "B";
            case "3" -> "C";
            case "4" -> "D";
            case "5" -> "E";
            default -> val; // return as-is if not mapped
        };
    }

    private Retry retryPolicy() {
        // จะ retry ตามจำนวนที่ตั้งใน properties (เช่น 2 ครั้ง)
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
                    Map<?, ?> component = (Map<?, ?>) json.get("component");
                    if (component == null) {
                        throw new IllegalStateException("No 'component' in response for key=" + projectKey);
                    }

                    Object measuresObj = component.get("measures");
                    List<Map<String, Object>> measures = measuresObj instanceof List
                            ? (List<Map<String, Object>>) measuresObj
                            : Collections.emptyList();

                    Map<String, String> m = new HashMap<>();
                    for (var it : measures) {
                        m.put((String) it.get("metric"), String.valueOf(it.get("value")));
                    }

                    // เลือก maintainability key ที่เจอจริง
                    String maintainKey = MAINTAIN_KEYS.stream()
                            .filter(m::containsKey)
                            .findFirst()
                            .orElse(null);

                    Map<String, String> grades = new HashMap<>();
                    grades.put("security", mapGrade(m.get("security_rating")));
                    grades.put("reliability", mapGrade(m.get("reliability_rating")));
                    grades.put("maintainability", maintainKey == null ? null : mapGrade(m.get(maintainKey)));

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
                .takeUntil(List::isEmpty)
                .flatMapIterable(list -> list)
                .distinct()
                .collectList();
    }

    public Mono<List<SonarSummary>> getAllProjectSummaries(int pageSize, int maxPages) {
        return fetchAllProjectKeys(pageSize, maxPages).flatMap(this::getSummaries);
    }

    /* ===== CSV helper ===== */
    public String toCsv(List<SonarSummary> items) {
        List<String> headers = List.of(
                "projectKey",
                "securityGrade","reliabilityGrade","maintainabilityGrade",
                "coverage","duplication","bugs","vulnerabilities","codeSmells"
        );
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
        if (v.contains(",") || v.contains("\"")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    private String safe(Object v) { return v == null ? "" : v.toString(); }
}
