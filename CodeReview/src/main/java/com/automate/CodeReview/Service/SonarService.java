package com.automate.CodeReview.Service;

import com.automate.CodeReview.Config.SonarProperties;
import com.automate.CodeReview.dto.SonarSummary;
import com.automate.CodeReview.exception.SonarApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@Service
public class SonarService {

    private static final Logger log = LoggerFactory.getLogger(SonarService.class);

    /** PATH ของ Sonar API ที่ใช้บ่อย */
    private static final String MEASURES_PATH = "/api/measures/component";
    private static final String COMPONENT_SEARCH_PATH = "/api/components/search";

    /** timeout โดยรวมต่อ request ไป Sonar (วินาที) */
    private static final int DEFAULT_TIMEOUT_SEC = 10;

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

    /** grade mapping ของ Sonar (1..5 -> A..E) */
    private String mapGrade(Object raw) {
        if (raw == null) return "N/A";
        String val = raw.toString().trim();
        if (val.endsWith(".0")) {
            val = val.substring(0, val.length() - 2);
        }
        return switch (val) {
            case "1" -> "A";
            case "2" -> "B";
            case "3" -> "C";
            case "4" -> "D";
            case "5" -> "E";
            default -> val;
        };
    }

    /** ควร retry เมื่อไหร่บ้าง */
    private boolean shouldRetry(Throwable ex) {
        if (ex instanceof WebClientRequestException) return true; // network/DNS/connect reset
        if (ex instanceof TimeoutException) return true;
        if (ex instanceof IOException) return true;
        if (ex instanceof SonarApiException sae) {
            return sae.isServerError();
        }
        return false;
    }

    /** นโยบาย retry */
    private Retry retryPolicy() {
        int maxRetries = Math.max(props.getMaxRetries(), 0);
        return Retry
                .fixedDelay(maxRetries, Duration.ofMillis(800))
                .filter(this::shouldRetry);
    }

    /** ดึงสรุป metric ของโปรเจกต์เดียว */
    public Mono<SonarSummary> getSummary(String projectKey) {
        if (projectKey == null || projectKey.isBlank()) {
            return Mono.error(new IllegalArgumentException("projectKey is required"));
        }

        MultiValueMap<String, String> q = new LinkedMultiValueMap<>();
        q.add("component", projectKey);
        q.add("metricKeys", String.join(",", METRICS));

        return sonarClient.get()
                .uri(uriBuilder -> uriBuilder.path(MEASURES_PATH).queryParams(q).build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp ->
                        resp.bodyToMono(String.class).defaultIfEmpty("")
                                .flatMap(body -> Mono.error(
                                        SonarApiException.of(resp.statusCode().value(), MEASURES_PATH, body)))
                )
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(DEFAULT_TIMEOUT_SEC))
                .retryWhen(retryPolicy())
                .map(json -> mapMeasuresToSummary(json, projectKey));
    }

    /** map JSON -> SonarSummary (ปลอดภัยต่อ null/empty) */
    @SuppressWarnings("unchecked")
    private SonarSummary mapMeasuresToSummary(Map<?, ?> json, String projectKey) {
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
        String maintainKey = MAINTAIN_KEYS.stream().filter(m::containsKey).findFirst().orElse(null);

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
    }

    /**
     * ดึงหลายโปรเจกต์แบบ "partial-ok" (default)
     * - ถ้าโปรเจกต์ใดพัง จะข้าม (log warn) และคืนตัวที่เหลือ
     */
    public Mono<List<SonarSummary>> getSummaries(List<String> projectKeys) {
        int concurrency = Math.min(8, Math.max(1, projectKeys.size()));
        return Flux.fromIterable(projectKeys)
                .flatMap(pk ->
                                getSummary(pk)
                                        .doOnError(ex -> {
                                            if (ex instanceof SonarApiException sae) {
                                                if (sae.isServerError()) {
                                                    log.error("Skip project {} due to Sonar 5xx: status={}, msg={}, bodyPreview={}",
                                                            pk, sae.getStatusCode(), sae.getMessage(), sae.bodyPreview());
                                                } else {
                                                    log.warn("Skip project {} due to Sonar 4xx: status={}, msg={}, bodyPreview={}",
                                                            pk, sae.getStatusCode(), sae.getMessage(), sae.bodyPreview());
                                                }
                                            } else if (ex instanceof TimeoutException) {
                                                log.warn("Skip project {} due to timeout", pk);
                                            } else if (ex instanceof WebClientRequestException) {
                                                log.warn("Skip project {} due to network error: {}", pk, ex.getMessage());
                                            } else {
                                                log.warn("Skip project {} due to: {}", pk, ex.toString());
                                            }
                                        })
                                        .onErrorResume(ex -> Mono.empty())
                        , concurrency)
                .collectList();
    }

    /**
     * ดึงหลายโปรเจกต์แบบ strict (fail-fast)
     * - ถ้าโปรเจกต์ใดพัง จะ error ทั้งก้อน
     */
    public Mono<List<SonarSummary>> getSummariesStrict(List<String> projectKeys) {
        int concurrency = Math.min(8, Math.max(1, projectKeys.size()));
        return Flux.fromIterable(projectKeys)
                .flatMap(this::getSummary, concurrency)
                .collectList();
    }

    /** ดึง project keys ทั้งหมดแบบแบ่งหน้า */
    public Mono<List<String>> fetchAllProjectKeys(int pageSize, int maxPages) {
        int ps = Math.min(Math.max(pageSize, 1), 500); // 1..500

        return Flux.range(1, Math.max(maxPages, 1))
                .concatMap(page -> sonarClient.get()
                        .uri(uriBuilder -> uriBuilder.path(COMPONENT_SEARCH_PATH)
                                .queryParam("qualifiers", "TRK")
                                .queryParam("p", page)
                                .queryParam("ps", ps)
                                .build())
                        .accept(MediaType.APPLICATION_JSON)
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, resp ->
                                resp.bodyToMono(String.class).defaultIfEmpty("")
                                        .flatMap(body -> Mono.error(
                                                SonarApiException.of(resp.statusCode().value(), COMPONENT_SEARCH_PATH, body)))
                        )
                        .bodyToMono(Map.class)
                        .timeout(Duration.ofSeconds(DEFAULT_TIMEOUT_SEC))
                        .retryWhen(retryPolicy())
                        .map(json -> {
                            @SuppressWarnings("unchecked")
                            var comps = (List<Map<String, Object>>) json.getOrDefault("components", List.of());
                            return comps.stream()
                                    .map(c -> (String) c.get("key"))
                                    .filter(Objects::nonNull)
                                    .toList();
                        })
                )
                .takeUntil(List::isEmpty) // ถ้าหน้าไหนว่าง ถือว่าจบ
                .flatMapIterable(list -> list)
                .distinct()
                .collectList();
    }

    /** ดึงสรุปทุกโปรเจกต์ (partial-ok by default) */
    public Mono<List<SonarSummary>> getAllProjectSummaries(int pageSize, int maxPages) {
        return fetchAllProjectKeys(pageSize, maxPages).flatMap(this::getSummaries);
    }

    /* ===== CSV helper ===== */
    public String toCsv(List<SonarSummary> items) {
        List<String> headers = List.of(
                "projectKey",
                "securityGrade", "reliabilityGrade", "maintainabilityGrade",
                "coverage", "duplication", "bugs", "vulnerabilities", "codeSmells"
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
