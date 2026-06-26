package com.loopers.infrastructure.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * PG(pg-simulator) 호출용 RestClient.
 *
 * <p>connect/read 타임아웃을 분리해 설정한다. PG 의 동기 응답(접수 확인)은 100~500ms 안에 오므로
 * read timeout 은 그보다 충분히 위로 둔다(정상 요청을 죽이지 않도록). CircuitBreaker/Retry 는 후속 단계.</p>
 */
@Configuration
public class PgClientConfig {

    @Bean
    public RestClient pgRestClient(
            @Value("${pg.base-url}") String baseUrl,
            @Value("${pg.connect-timeout-ms:1000}") long connectTimeoutMs,
            @Value("${pg.read-timeout-ms:2000}") long readTimeoutMs
    ) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .withReadTimeout(Duration.ofMillis(readTimeoutMs));
        ClientHttpRequestFactory factory = ClientHttpRequestFactoryBuilder.detect().build(settings);

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }
}
