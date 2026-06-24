package com.loopers.infrastructure.pg;

import feign.Request;
import feign.RetryableException;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import java.util.concurrent.TimeUnit;

public class PgFeignClientConfig {

    @Bean
    public Request.Options options() {
        return new Request.Options(3000, TimeUnit.MILLISECONDS, 5000, TimeUnit.MILLISECONDS, true);
    }

    @Bean
    public Retryer retryer() {
        // 초기 100ms 대기, 최대 500ms, 총 3번 시도 (최초 1회 + 재시도 2회)
        return new Retryer.Default(100, 500, 3);
    }

    @Bean
    public ErrorDecoder errorDecoder() {
        return (methodKey, response) -> {
            if (response.status() >= 500) {
                return new RetryableException(
                    response.status(),
                    response.reason(),
                    response.request().httpMethod(),
                    (Long) null,
                    response.request()
                );
            }
            return feign.FeignException.errorStatus(methodKey, response);
        };
    }
}
