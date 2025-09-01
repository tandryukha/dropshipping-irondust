package com.irondust.search.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class VectorClientConfig {

    @Bean(name = "qdrantClient")
    public WebClient qdrantClient(VectorProperties vectorProperties) {
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
        WebClient.Builder builder = WebClient.builder()
                .baseUrl(vectorProperties.getHost())
                .exchangeStrategies(strategies)
                .defaultHeaders(headers -> headers.setAccept(MediaType.parseMediaTypes("application/json")));
        if (vectorProperties.getApiKey() != null && !vectorProperties.getApiKey().isBlank()) {
            builder.defaultHeader("api-key", vectorProperties.getApiKey());
        }
        return builder.build();
    }
}


