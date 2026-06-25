package com.loopers.infrastructure.pg;

import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class PgClientConfig {

    @Bean("pgRestTemplate")
    public RestTemplate pgRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3_000);
        factory.setReadTimeout(1_000);
        return new RestTemplate(factory);
    }

    @Bean
    public CircuitBreaker pgPaymentCircuitBreaker(CircuitBreakerFactory<?, ?> circuitBreakerFactory) {
        return circuitBreakerFactory.create("pg-payment");
    }
}
