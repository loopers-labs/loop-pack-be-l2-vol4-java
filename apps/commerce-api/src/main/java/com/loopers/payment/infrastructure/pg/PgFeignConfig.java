package com.loopers.payment.infrastructure.pg;

import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;

public class PgFeignConfig {

    @Bean
    public ErrorDecoder errorDecoder() {
        return new PgErrorDecoder();
    }
}
