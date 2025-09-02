package com.automate.CodeReview.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SonarClient {
    private final WebClient sonarWebClient;

    public List<Map<String, String>> fetchProjects(int pageSize) {
        int p = 1;
        List<Map<String,String>> out = new ArrayList<>();
        while (true) {
            int finalP = p;
            JsonNode root = sonarWebClient.get()
                    .uri(uri -> uri.path("/api/projects/search")
                            .queryParam("ps", pageSize)
                            .queryParam("p", finalP)
                            .build())
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            JsonNode items = root.has("components") ? root.get("components")
                    : root.get("projects"); // เผื่อบางเวอร์ชัน
            if (items == null || !items.elements().hasNext()) break;

            for (JsonNode n : items) {
                out.add(Map.of(
                        "key",  n.path("key").asText(),
                        "name", n.path("name").asText()
                ));
            }
            p++;
        }
        return out;
    }

    public JsonNode fetchMeasures(String projectKey) {
        // เมตริกหลักที่อยากเก็บรวมเป็น JSONB ใน scans.metrics
        String metrics = String.join(",",
                "alert_status","bugs","vulnerabilities","code_smells",
                "coverage","duplicated_lines_density");
        return sonarWebClient.get()
                .uri(uri -> uri.path("/api/measures/component")
                        .queryParam("component", projectKey)
                        .queryParam("metricKeys", metrics)
                        .build())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
    }

    public List<JsonNode> fetchIssues(String projectKey) {
        int p = 1;
        List<JsonNode> all = new ArrayList<>();
        while (true) {
            int finalP = p;
            JsonNode root = sonarWebClient.get()
                    .uri(uri -> uri.path("/api/issues/search")
                            .queryParam("componentKeys", projectKey)
                            .queryParam("ps", 500)
                            .queryParam("p", finalP)
                            .build())
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            ArrayNode issues = (ArrayNode) root.path("issues");
            if (issues == null || issues.isEmpty()) break;

            for (JsonNode i : issues) all.add(i);
            p++;
        }
        return all;
    }
}
