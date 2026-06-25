package com.loopers.infrastructure.payment;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class PgClientConfig {

    /**
     * PG 전용 RestTemplate. 동기 호출의 "타임아웃"인 connect/read timeout 을 ClientHttpRequestFactory 에 건다.
     */
    @Bean
    public RestTemplate pgRestTemplate(PgClientProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) properties.connectTimeout().toMillis());
        factory.setReadTimeout((int) properties.readTimeout().toMillis());
        return new RestTemplate(factory);
    }
}
