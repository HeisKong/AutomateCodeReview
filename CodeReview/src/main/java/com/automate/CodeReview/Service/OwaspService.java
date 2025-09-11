package com.automate.CodeReview.Service; // ใช้ตัวเล็ก
import com.automate.CodeReview.Utilities.OwaspUtil;
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

@Service @RequiredArgsConstructor
public class OwaspService {
    private final SonarClient sonar;
    private final OwaspIssueRepository issueRepo;
    private final OwaspSummaryRepository summaryRepo;

    @Value("${sonar.baseUrl}") private String sonarBaseUrl;
    @Value("${sonar.legacyFacets:true}") private boolean legacy;

    @Transactional
    public String syncAndSummarize(String projectKey, String branch) {
        final String br = (branch == null ? "" : branch);

        // 1) ดึง facets + หน้าแรก (เฉพาะ VULNERABILITY เพราะข้อ 1)
        IssuesSearchResponse head = sonar.fetchIssuesAndFacets(projectKey, br, 1, 500);
        if (head == null) {
            throw new IllegalStateException("Sonar API returned null response");
        }

        // ---- guard: ถ้าผลลัพธ์ไม่มีอะไรเลย ให้หยุดและแจ้ง 400 แทนการ upsert ----
        boolean noIssues = head.getTotal() <= 0;
        boolean noFacets = head.getFacets() == null || head.getFacets().isEmpty();
        if (noIssues && noFacets) {
            throw new IllegalArgumentException("No vulnerabilities for projectKey='" + projectKey +
                    "' branch='" + br + "'. Check project key/branch in SonarQube.");
        }

        // 2) ดึงทุก issue แล้วบันทึก (ให้แน่ใจว่า issueRepo ถูก inject และมี method findByIssueKey)
        var all = sonar.fetchAllIssues(projectKey, br);
        if (all == null || all.isEmpty()) {
            // ถ้าต้องการบังคับว่าต้องมี issue อย่างน้อย 1 รายการ ให้โยน 400
            // throw new IllegalArgumentException("Vulnerabilities list is empty.");
        } else {
            final int BATCH = 500;
            List<OwaspIssue> buf = new ArrayList<>(BATCH);
            for (var is : all) {
                buf.add(upsertIssue(projectKey, br, is));
                if (buf.size() >= BATCH) { issueRepo.saveAll(buf); buf.clear(); }
            }
            if (!buf.isEmpty()) issueRepo.saveAll(buf);
        }

        // 3) สร้าง summary JSON (string)
        String json = buildSummaryJson(projectKey, br, head, legacy);

        // 4) upsert ลง owasp_summary (summary_json เป็น jsonb -> แปลงเป็น JsonNode)
        var rec = summaryRepo.findByProjectKeyAndBranch(projectKey, br)
                .orElseGet(OwaspSummary::new);
        rec.setProjectKey(projectKey);
        rec.setBranch(br);
        try {
            JsonNode node = new ObjectMapper().readTree(json);
            rec.setSummaryJson(node);
        } catch (Exception e) {
            throw new RuntimeException("Invalid summary JSON", e);
        }

        Instant now = Instant.now();
        if (rec.getId() == null) rec.setCreatedAt(now);
        rec.setUpdatedAt(now);
        summaryRepo.save(rec);

        return json;
    }


    private OwaspIssue upsertIssue(String projectKey, String branch, IssuesSearchResponse.Issue is){
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
        rec.setTags(is.getTags()==null?null:String.join(",", is.getTags()));
        rec.setCreationDate(OwaspUtil.parseSonarInstant(is.getCreationDate()));
        rec.setUpdateDate(OwaspUtil.parseSonarInstant(is.getUpdateDate()));
        rec.setEffort(is.getEffort());
        String code = OwaspUtil.extractOwaspCodeFromTags(is.getTags());
        rec.setOwaspCode(code);
        rec.setOwaspName(code==null?null:OwaspUtil.owaspName(code));
        return rec;
    }

    private String extractPath(String component, String projectKey){
        if (component==null) return null;
        int i = component.indexOf(projectKey + ":");
        return (i>=0) ? component.substring(i + projectKey.length() + 1) : component;
    }

    private String buildSummaryJson(String projectKey, String branch, IssuesSearchResponse data, boolean legacySevFacet){
        Map<String, List<IssuesSearchResponse.FacetValue>> f = Optional.ofNullable(data.getFacets())
                .orElseGet(List::of)
                .stream().collect(Collectors.toMap(IssuesSearchResponse.Facet::getProperty, IssuesSearchResponse.Facet::getValues));

        String sevProp = legacySevFacet ? "severities" : "impactSeverities";
        int critical=0, high=0, medium=0, low=0;
        for (var v : f.getOrDefault(sevProp, List.of())){
            String k = v.getVal().toUpperCase(Locale.ROOT);
            switch (k){
                case "CRITICAL" -> critical += v.getCount();
                case "HIGH"     -> high     += v.getCount();
                case "MEDIUM"   -> medium   += v.getCount();
                case "LOW"      -> low      += v.getCount();
                // mapping legacy severities
                case "BLOCKER"  -> critical += v.getCount();
                case "MAJOR"    -> high     += v.getCount();
                case "MINOR"    -> medium   += v.getCount();
                case "INFO"     -> low      += v.getCount();
            }
        }
        int total = data.getTotal();

        // OWASP 2021
        List<Map<String,Object>> owasp2021 = new ArrayList<>();
        for (int i=1;i<=10;i++){
            String id = "A"+String.format("%02d", i);
            String keyLower = "a"+String.format("%02d", i);
            int count = f.getOrDefault("owaspTop10-2021", List.of()).stream()
                    .filter(x->keyLower.equalsIgnoreCase(x.getVal()))
                    .mapToInt(IssuesSearchResponse.FacetValue::getCount).findFirst().orElse(0);
            String status = (count==0) ? "pass" : (count<=3 ? "warn" : "fail");
            owasp2021.add(Map.of("id", id, "name", OwaspUtil.owaspName(id), "count", count, "status", status));
        }

        // Hot Security (Top 5)
        List<Map<String,Object>> hot = f
                .getOrDefault("sonarsourceSecurity", Collections.<IssuesSearchResponse.FacetValue>emptyList())
                .stream()
                .sorted((a,b) -> Integer.compare(b.getCount(), a.getCount()))
                .limit(5)
                .map(v -> {
                    Map<String,Object> m = new HashMap<>();
                    m.put("category", v.getVal());
                    m.put("count", v.getCount());
                    return m;
                })
                .collect(Collectors.toList());

        // คะแนน / ความเสี่ยงอย่างง่าย
        int penalty = critical*8 + high*4 + medium*2 + low;
        int score = Math.max(0, 100 - Math.min(90, penalty));
        String risk = (critical>0 || high>=5) ? "HIGH" : (high>0 || medium>=5) ? "MEDIUM" : "LOW";

        String issuesUrl = sonarBaseUrl + "/project/issues?id=" + enc(projectKey)
                + ((branch!=null && !branch.isBlank()) ? "&branch="+enc(branch) : "")
                + "&types=VULNERABILITY";

        Map<String,Object> root = new LinkedHashMap<>();
        root.put("branch", branch);
        root.put("securityScore", score);
        root.put("riskLevel", risk);
        root.put("severities", Map.of("critical",critical,"high",high,"medium",medium,"low",low,"total",total));
        root.put("owasp2021", owasp2021);
        root.put("hotSecurity", hot);
        root.put("links", Map.of("issues", issuesUrl));

        try { return new ObjectMapper().writeValueAsString(root); }
        catch (Exception e){ throw new RuntimeException(e); }
    }

    private String enc(String s){ return URLEncoder.encode(s, StandardCharsets.UTF_8); }
}
