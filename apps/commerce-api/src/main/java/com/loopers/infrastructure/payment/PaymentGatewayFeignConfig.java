package com.loopers.infrastructure.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Request;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class PaymentGatewayFeignConfig {

    @Bean
    public ErrorDecoder errorDecoder(ObjectMapper objectMapper) {
        return new PaymentGatewayErrorDecoder(objectMapper);
    }

    @Bean
    public Request.Options options(PaymentGatewayProperties properties) {
        return new Request.Options(
                properties.connectTimeoutMillis(), TimeUnit.MILLISECONDS,
                properties.readTimeoutMillis(), TimeUnit.MILLISECONDS,
                true
        );
    }
}
