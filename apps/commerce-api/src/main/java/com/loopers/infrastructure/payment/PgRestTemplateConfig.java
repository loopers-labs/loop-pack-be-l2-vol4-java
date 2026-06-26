package com.loopers.infrastructure.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * PG 호출용 RestTemplate Bean. connect/read timeout 을 분리 설정해 외부 시스템 지연이
 * 우리 자원을 점유하지 못하게 막는다.
 *
 *  - Qualifier 로 분리해 다른 RestTemplate (브랜드 알림 등) 과 섞이지 않게 한다.
 *  - 실제 timeout 값은 payment.yml 의 pg.simulator.* 에서 읽는다.
 */
@Configuration
public class PgRestTemplateConfig {

    @Bean("pgRestTemplate")
    public RestTemplate pgRestTemplate(
        RestTemplateBuilder builder,
        @Value("${pg.simulator.connect-timeout}") Duration connectTimeout,
        @Value("${pg.simulator.read-timeout}") Duration readTimeout
    ) {
        return builder
            .connectTimeout(connectTimeout)
            .readTimeout(readTimeout)
            .build();
    }
}
