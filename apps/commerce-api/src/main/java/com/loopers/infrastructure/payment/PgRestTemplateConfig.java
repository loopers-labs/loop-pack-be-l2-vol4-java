package com.loopers.infrastructure.payment;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class PgRestTemplateConfig {

    @Bean
    public RestTemplate pgRestTemplate(RestTemplateBuilder builder) {
        return builder.build();
    }
}
