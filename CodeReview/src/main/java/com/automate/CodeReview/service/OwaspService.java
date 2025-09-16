package com.automate.CodeReview.service;

import com.automate.CodeReview.utilities.OwaspUtil;
import com.automate.CodeReview.client.SonarClient;
import com.automate.CodeReview.dto.IssuesSearchResponse;
import com.automate.CodeReview.repository.OwaspIssueRepository;
import com.automate.CodeReview.repository.OwaspSummaryRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import com.automate.CodeReview.entity.OwaspIssue;
import com.automate.CodeReview.entity.OwaspSummary;

@Service
@RequiredArgsConstructor
public class OwaspService {

    private final SonarClient sonar;
    private final OwaspIssueRepository issueRepo;
    private final OwaspSummaryRepository summaryRepo;
    private final ObjectMapper mapper; // inject มาจาก Spring

    @Value("${sonar.baseUrl}") private String sonarBaseUrl;
    @Value("${sonar.legacyFacets:true}") private boolean legacy;

    @Transactional
    public String syncAndSummarize(String projectKeyInput, String branch) {
        final String br = (branch == null ? "" : branch);

        String projectKey = sonar.resolveProjectKey(projectKeyInput);

        IssuesSearchResponse head = sonar.fetchIssuesAndFacets(projectKey, br, 1, 500);
        if (head == null) {
            throw new IllegalStateException("No response from SonarQube for projectKey='" + projectKey + "'");
        }

        boolean noIssues = head.getTotal() <= 0;
        boolean noFacets = head.getFacets() == null || head.getFacets().isEmpty();
        if (noIssues && noFacets) {
            throw new IllegalArgumentException("No vulnerabilities for projectKey='" + projectKey +
                    "' branch='" + br + "'.");
        }

        // fetch all issues & upsert
        var all = sonar.fetchAllIssues(projectKey, br);
        if (all != null && !all.isEmpty()) {
            final int BATCH = 500;
            List<OwaspIssue> buf = new ArrayList<>(BATCH);
            for (var is : all) {
                buf.add(upsertIssue(projectKey, br, is));
                if (buf.size() >= BATCH) {
                    issueRepo.saveAll(buf);
                    buf.clear();
                }
            }
            if (!buf.isEmpty()) issueRepo.saveAll(buf);
        }

        // build summary JSON
        String json = buildSummaryJson(projectKey, br, head, legacy);

        // upsert summary
        var rec = summaryRepo.findByProjectKeyAndBranch(projectKey, br)
                .orElseGet(OwaspSummary::new);
        rec.setProjectKey(projectKey);
        rec.setBranch(br);

        try {
            rec.setSummaryJson(mapper.readTree(json));  // ✅ ไม่มีขีดแดงแล้ว
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException("Invalid summary JSON", e);
        }

        Instant now = Instant.now();
        if (rec.getId() == null) rec.setCreatedAt(now);
        rec.setUpdatedAt(now);

        summaryRepo.save(rec);

        return json;
    }

    private OwaspIssue upsertIssue(String projectKey, String branch, IssuesSearchResponse.Issue is) {
        OwaspIssue rec = issueRepo.findByIssueKey(is.getKey()).orElseGet(OwaspIssue::new);
        rec.setIssueKey(is.getKey());
        rec.setProjectKey(projectKey);
        rec.setBranch(branch);
        rec.setRuleKey(is.getRule());
        rec.setMessage(is.getMessage());
        rec.setComponent(is.getComponent());
        rec.setFilePath(extractPath(is.getComponent(), projectKey));
        rec.setLineNumber(is.getLine());
        rec.setSeverityRaw(is.getSeverity());
        rec.setTypeRaw(is.getType());
        rec.setTags(is.getTags() == null ? null : String.join(",", is.getTags()));
        rec.setCreationDate(OwaspUtil.parseSonarInstant(is.getCreationDate()));
        rec.setUpdateDate(OwaspUtil.parseSonarInstant(is.getUpdateDate()));
        rec.setEffort(is.getEffort());
        String code = OwaspUtil.extractOwaspCodeFromTags(is.getTags());
        rec.setOwaspCode(code);
        rec.setOwaspName(code == null ? null : OwaspUtil.owaspName(code));
        return rec;
    }

    private String extractPath(String component, String projectKey) {
        if (component == null) return null;
        String prefix = projectKey + ":";
        return component.startsWith(prefix) ? component.substring(prefix.length()) : component;
    }

    private String buildSummaryJson(String projectKey, String branch,
                                    IssuesSearchResponse data, boolean legacySevFacet) {
        Map<String, List<IssuesSearchResponse.FacetValue>> f = Optional.ofNullable(data.getFacets())
                .orElseGet(List::of)
                .stream()
                .collect(Collectors.toMap(IssuesSearchResponse.Facet::getProperty, IssuesSearchResponse.Facet::getValues));

        String sevProp = legacySevFacet ? "severities" : "impactSeverities";
        int critical=0, high=0, medium=0, low=0;
        for (var v : f.getOrDefault(sevProp, List.of())) {
            String k = v.getVal().toUpperCase(Locale.ROOT);
            switch (k) {
                case "CRITICAL", "BLOCKER" -> critical += v.getCount();
                case "HIGH", "MAJOR"       -> high     += v.getCount();
                case "MEDIUM", "MINOR"     -> medium   += v.getCount();
                case "LOW", "INFO"         -> low      += v.getCount();
            }
        }
        int total = data.getTotal();

        List<Map<String,Object>> owasp2021 = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            String id = "A" + String.format("%02d", i);
            String keyLower = id.toLowerCase(Locale.ROOT);
            int count = f.getOrDefault("owaspTop10-2021", List.of()).stream()
                    .filter(x -> keyLower.equalsIgnoreCase(x.getVal()))
                    .mapToInt(IssuesSearchResponse.FacetValue::getCount)
                    .sum(); // 👈 ใช้ sum() แทน findFirst()
            String status = (count == 0) ? "pass" : (count <= 3 ? "warn" : "fail");
            owasp2021.add(Map.of("id", id, "name", OwaspUtil.owaspName(id),
                    "count", count, "status", status));
        }

        List<Map<String, Object>> hot = f
                .getOrDefault("sonarsourceSecurity",
                        Collections.<IssuesSearchResponse.FacetValue>emptyList())
                .stream()
                .sorted(Comparator.comparingInt(IssuesSearchResponse.FacetValue::getCount).reversed())
                .limit(5)
                .map(v -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("category", v.getVal());
                    m.put("count", v.getCount());
                    return m;
                })
                .collect(Collectors.toList());

        int penalty = critical*8 + high*4 + medium*2 + low;
        int score = Math.max(0, 100 - Math.min(90, penalty));
        String risk = (critical > 0 || high >= 5) ? "HIGH" :
                (high > 0 || medium >= 5) ? "MEDIUM" : "LOW";

        String issuesUrl = sonarBaseUrl + "/project/issues?id=" + enc(projectKey)
                + ((branch != null && !branch.isBlank()) ? "&branch=" + enc(branch) : "")
                + "&types=VULNERABILITY";

        Map<String,Object> root = new LinkedHashMap<>();
        root.put("branch", branch);
        root.put("securityScore", score);
        root.put("riskLevel", risk);
        root.put("severities", Map.of("critical",critical,"high",high,"medium",medium,"low",low,"total",total));
        root.put("owasp2021", owasp2021);
        root.put("hotSecurity", hot);
        root.put("links", Map.of("issues", issuesUrl));

        try {
            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize summary", e);
        }
    }

    private String enc(String s){ return URLEncoder.encode(s, StandardCharsets.UTF_8); }
}
