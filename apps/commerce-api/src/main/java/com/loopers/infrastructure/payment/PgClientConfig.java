package com.loopers.infrastructure.payment;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class PgClientConfig {

    @Bean("pgRequestRestTemplate")
    public RestTemplate pgRequestRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(500);
        factory.setReadTimeout(1000); // requestPayment: 1s
        return new RestTemplate(factory);
    }

    @Bean("pgQueryRestTemplate")
    public RestTemplate pgQueryRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(500);
        factory.setReadTimeout(15000); // 1차 Poll: 15s
        return new RestTemplate(factory);
    }
}
