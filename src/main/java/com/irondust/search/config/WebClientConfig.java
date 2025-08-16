package com.irondust.search.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }

    @Bean(name = "wooClient")
    public WebClient wooClient(AppProperties appProperties) {
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
        return WebClient.builder()
                .baseUrl(appProperties.getBaseUrl())
                .exchangeStrategies(strategies)
                .defaultHeaders(headers -> headers.setAccept(MediaType.parseMediaTypes("application/json")))
                .build();
    }

    @Bean(name = "meiliClient")
    public WebClient meiliClient(MeiliProperties meiliProperties) {
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
        return WebClient.builder()
                .baseUrl(meiliProperties.getHost())
                .exchangeStrategies(strategies)
                .defaultHeader("Authorization", "Bearer " + meiliProperties.getKey())
                .defaultHeaders(headers -> headers.setAccept(MediaType.parseMediaTypes("application/json")))
                .build();
    }
}



