package com.loopers.infrastructure.pg;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Request;
import feign.RetryableException;
import feign.Retryer;
import feign.codec.Decoder;
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
        return Retryer.NEVER_RETRY;
    }

    @Bean
    public Decoder decoder() {
        return new PgDataUnwrappingDecoder(new ObjectMapper());
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
