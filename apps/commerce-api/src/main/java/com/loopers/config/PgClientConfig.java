package com.loopers.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
@RequiredArgsConstructor
public class PgClientConfig {

    private final PgProperties pgProperties;

    @Bean
    public RestTemplate pgRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(pgProperties.getConnectTimeout());
        factory.setReadTimeout(pgProperties.getReadTimeout());
        return new RestTemplate(factory);
    }
}
