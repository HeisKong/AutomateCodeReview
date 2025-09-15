package com.automate.CodeReview.Config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

@Configuration
public class WebClientConfig {

    private final SonarProperties sonarProperties;

    public WebClientConfig(SonarProperties sonarProperties){
        this.sonarProperties = sonarProperties;
    }

    @Bean("sonarWebClient")
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
