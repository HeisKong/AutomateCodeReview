package com.automate.CodeReview.client;

import com.automate.CodeReview.dto.IssuesSearchResponse;
import com.automate.CodeReview.service.SonarTokenService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class SonarClient {

    @Value("${sonar.baseUrl}")
    private String baseUrl;

    @Value("${sonar.legacyFacets:true}")
    private boolean legacyFacets;

    private final SonarTokenService tokenService;

    private final ObjectMapper mapper = new ObjectMapper();

    /** RestTemplate + timeout */
    private final RestTemplate rest = buildRest();
    private static RestTemplate buildRest() {
        var f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(10_000);
        f.setReadTimeout(30_000);
        return new RestTemplate(f);
    }

    /** small in-memory cache for resolved keys */
    private final Map<String,String> keyCache = new ConcurrentHashMap<>();

    /** Authorization: Basic (token:) */
    private HttpHeaders authHeaders() {
        String token = tokenService.getToken();
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Sonar token not set. POST /api/sonar/token first.");
        }
        String basic = Base64.getEncoder()
                .encodeToString((token + ":").getBytes(StandardCharsets.UTF_8));
        HttpHeaders h = new HttpHeaders();
        h.set(HttpHeaders.AUTHORIZATION, "Basic " + basic);
        h.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        return h;
    }

    /** หา key ที่ถูกต้อง (รองรับการพิมพ์ชื่อสั้น เช่น apitest) */
    public String resolveProjectKey(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("ProjectKey is blank.");
        }
        // hit cache first
        String cached = keyCache.get(input.toLowerCase(Locale.ROOT));
        if (cached != null) return cached;

        // 1) exact by key: ?projects=
        var uri1 = UriComponentsBuilder.fromHttpUrl(baseUrl + "/api/projects/search")
                .queryParam("projects", input)
                .queryParam("ps", 100).queryParam("p", 1)
                .build(true).toUri();
        try {
            ResponseEntity<String> r1 = rest.exchange(uri1, HttpMethod.GET, new HttpEntity<>(authHeaders()), String.class);
            if (r1.getStatusCode().is2xxSuccessful() && r1.getBody() != null) {
                JsonNode root = mapper.readTree(r1.getBody());
                if (root.path("paging").path("total").asInt(0) > 0) {
                    String key = root.path("components").get(0).path("key").asText();
                    keyCache.put(input.toLowerCase(Locale.ROOT), key);
                    return key;
                }
            }
        } catch (HttpStatusCodeException ignore) {
            // fallthrough to q-search
        } catch (Exception ignore) {
            // fallthrough
        }

        // 2) fuzzy by name/key: ?q=
        var uri2 = UriComponentsBuilder.fromHttpUrl(baseUrl + "/api/projects/search")
                .queryParam("q", input)
                .queryParam("ps", 100).queryParam("p", 1)
                .build(true).toUri();
        try {
            ResponseEntity<String> r2 = rest.exchange(uri2, HttpMethod.GET, new HttpEntity<>(authHeaders()), String.class);
            if (!r2.getStatusCode().is2xxSuccessful() || r2.getBody() == null) {
                throw new IllegalArgumentException();
            }
            JsonNode comps = mapper.readTree(r2.getBody()).path("components");
            if (!comps.isArray() || comps.size() == 0) throw new IllegalArgumentException();

            String exactKey = null, endsWith = null, exactName = null, first = comps.get(0).path("key").asText();
            for (JsonNode c : comps) {
                String key = c.path("key").asText("");
                String name = c.path("name").asText("");
                if (key.equalsIgnoreCase(input))                 exactKey  = key;
                if (key.endsWith(":" + input))                   endsWith  = key;
                if (name.equalsIgnoreCase(input))                exactName = key;
            }
            String chosen = exactKey != null ? exactKey : endsWith != null ? endsWith : exactName != null ? exactName : first;
            keyCache.put(input.toLowerCase(Locale.ROOT), chosen);
            return chosen;
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "ProjectKey '" + input + "' does not exist in SonarQube. " +
                            "Try the full key shown in Sonar (e.g. 'com.example:apitest').");
        }
    }

    /** true ถ้า resolve ได้ */
    public boolean projectExists(String input) {
        try { resolveProjectKey(input); return true; } catch (Exception e) { return false; }
    }

    /** ดึง issues + facets */
    public IssuesSearchResponse fetchIssuesAndFacets(String projectKey, String branch, int page, int size) {
        String sevFacet = legacyFacets ? "severities" : "impactSeverities";
        String facets   = String.join(",", "owaspTop10-2021", sevFacet, "sonarsourceSecurity");

        var uri = UriComponentsBuilder.fromHttpUrl(baseUrl + "/api/issues/search")
                .queryParam("componentKeys", projectKey)
                .queryParam("types", "VULNERABILITY")
                .queryParam("facets", facets)
                .queryParam("p", page).queryParam("ps", size);
        if (branch != null && !branch.isBlank()) uri.queryParam("branch", branch);

        ResponseEntity<IssuesSearchResponse> res = rest.exchange(
                uri.build(true).toUri(), HttpMethod.GET, new HttpEntity<>(authHeaders()), IssuesSearchResponse.class);
        return res.getBody();
    }

    /** ดึงทุกหน้า */
    public List<IssuesSearchResponse.Issue> fetchAllIssues(String projectKey, String branch) {
        List<IssuesSearchResponse.Issue> all = new ArrayList<>();
        int page = 1, size = 500;
        while (true) {
            var uri = UriComponentsBuilder.fromHttpUrl(baseUrl + "/api/issues/search")
                    .queryParam("componentKeys", projectKey)
                    .queryParam("types", "VULNERABILITY")
                    .queryParam("p", page).queryParam("ps", size);
            if (branch != null && !branch.isBlank()) uri.queryParam("branch", branch);

            ResponseEntity<IssuesSearchResponse> res = rest.exchange(
                    uri.build(true).toUri(), HttpMethod.GET, new HttpEntity<>(authHeaders()), IssuesSearchResponse.class);

            var body = res.getBody();
            if (body == null || body.getIssues() == null || body.getIssues().isEmpty()) break;

            all.addAll(body.getIssues());
            if (body.getIssues().size() < size) break;
            page++;
        }
        return all;
    }

    /* สะดวกเรียกด้วยชื่อสั้น */
    public IssuesSearchResponse fetchIssuesAndFacetsByAnyKey(String inputKey, String branch, int page, int size) {
        return fetchIssuesAndFacets(resolveProjectKey(inputKey), branch, page, size);
    }
    public List<IssuesSearchResponse.Issue> fetchAllIssuesByAnyKey(String inputKey, String branch) {
        return fetchAllIssues(resolveProjectKey(inputKey), branch);
    }
}
