package com.automate.CodeReview.Config;

import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.HttpHeaders;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Configuration
public class SonarApiConfig {

    @Bean
    public WebClient sonarWebClient(
            @Value("${sonar.api.base-url}") String baseUrl,
            @Value("${sonar.api.token}") String token) {

        String basic = Base64.getEncoder().encodeToString((token + ":").getBytes(StandardCharsets.UTF_8));

        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                .build();
    }
}
