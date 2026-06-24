package com.loopers.infrastructure.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PaymentGatewayFeignConfig {

    @Bean
    public ErrorDecoder errorDecoder(ObjectMapper objectMapper) {
        return new PaymentGatewayErrorDecoder(objectMapper);
    }

    @Bean
    public Retryer retryer() {
        return new Retryer.Default(100, 300, 3);
    }
}
