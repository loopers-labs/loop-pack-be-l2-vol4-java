package com.loopers.tddstudy.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class PgClientConfig {

    @Value("${pg.timeout.connect}")
    private int connectTimeout;

    @Value("${pg.timeout.read}")
    private int readTimeout;

    @Bean(name = "pgRestTemplate")
    public RestTemplate pgRestTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofMillis(connectTimeout))
                .readTimeout(Duration.ofMillis(readTimeout))
                .build();
    }

    @Bean(name = "pgThreadPool")
    public ExecutorService pgThreadPool() {
        return Executors.newFixedThreadPool(50);
    }
}
