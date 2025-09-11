package com.automate.CodeReview.client;
import com.automate.CodeReview.Service.SonarTokenService;
import com.automate.CodeReview.dto.IssuesSearchResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.ArrayList;
import java.util.List;

@Component @RequiredArgsConstructor
public class SonarClient {
    @Value("${sonar.baseUrl}") private String baseUrl;
    @Value("${sonar.legacyFacets:true}") private boolean legacyFacets;
    private final SonarTokenService tokenService;

    private HttpHeaders authHeaders() {
        String token = tokenService.getToken();
        if (token == null) throw new IllegalStateException("Sonar token not set. POST /api/sonar/token first.");
        String basic = Base64.getEncoder().encodeToString((token + ":").getBytes(StandardCharsets.UTF_8));
        HttpHeaders h = new HttpHeaders(); h.set("Authorization","Basic " + basic); return h;
    }

    public IssuesSearchResponse fetchIssuesAndFacets(String projectKey, String branch, int page, int size){
        String sevFacet = legacyFacets ? "severities" : "impactSeverities";
        String facets   = String.join(",", "owaspTop10-2021", sevFacet, "sonarsourceSecurity");

        UriComponentsBuilder uri = UriComponentsBuilder
                .fromHttpUrl(baseUrl + "/api/issues/search")
                .queryParam("componentKeys", projectKey)
                .queryParam("types", "VULNERABILITY")
                .queryParam("facets", facets)
                .queryParam("p", page).queryParam("ps", size);
        if (branch != null && !branch.isBlank()) uri.queryParam("branch", branch);

        RestTemplate rt = new RestTemplate();
        ResponseEntity<IssuesSearchResponse> res = rt.exchange(
                uri.build(true).toUri(), HttpMethod.GET, new HttpEntity<>(authHeaders()), IssuesSearchResponse.class
        );
        return res.getBody();
    }

    /** ถ้าต้องการเก็บ issues ครบทุกหน้า */
    public List<IssuesSearchResponse.Issue> fetchAllIssues(String projectKey, String branch){
        RestTemplate rt = new RestTemplate();
        List<IssuesSearchResponse.Issue> all = new ArrayList<>();
        int page=1, size=500;
        while (true){
            UriComponentsBuilder uri = UriComponentsBuilder
                    .fromHttpUrl(baseUrl + "/api/issues/search")
                    .queryParam("componentKeys", projectKey)
                    .queryParam("types", "VULNERABILITY")
                    .queryParam("p", page).queryParam("ps", size);
            if (branch != null && !branch.isBlank()) uri.queryParam("branch", branch);

            ResponseEntity<IssuesSearchResponse> res = rt.exchange(
                    uri.build(true).toUri(), HttpMethod.GET, new HttpEntity<>(authHeaders()), IssuesSearchResponse.class);

            var body = res.getBody();
            if (body == null || body.getIssues() == null || body.getIssues().isEmpty()) break;
            all.addAll(body.getIssues());
            if (body.getIssues().size() < size) break;
            page++;
        }
        return all;
    }
}

