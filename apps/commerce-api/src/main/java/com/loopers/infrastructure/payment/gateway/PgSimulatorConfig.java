package com.loopers.infrastructure.payment.gateway;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class PgSimulatorConfig {

    @Bean
    public RestTemplate pgSimulatorRestTemplate(RestTemplateBuilder builder, PgSimulatorProperties properties) {
        return builder
            .connectTimeout(properties.getConnectTimeout())
            .readTimeout(properties.getReadTimeout())
            .build();
    }
}
