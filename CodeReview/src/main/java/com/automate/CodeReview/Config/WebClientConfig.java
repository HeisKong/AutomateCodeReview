package com.automate.CodeReview.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Configuration
public class WebClientConfig {

    private final SonarProperties sonarProperties;

    public WebClientConfig(SonarProperties sonarProperties){
        this.sonarProperties = sonarProperties;
    }

    @Bean
    public WebClient sonarWebClient() {
        WebClient.Builder b = WebClient.builder().baseUrl(sonarProperties.getHostUrl());
        if (sonarProperties.getServiceToken()!=null && !sonarProperties.getServiceToken().isBlank()) {
            String basic = "Basic " + Base64.getEncoder()
                    .encodeToString((sonarProperties.getServiceToken() + ":").getBytes(StandardCharsets.UTF_8));
            b.defaultHeader(HttpHeaders.AUTHORIZATION, basic);
        }
        return b.build();
    }
}
