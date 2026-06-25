package com.loopers.infrastructure.payment;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PaymentGatewayRetryConfig {

    @Bean
    public Retry paymentGatewayRetry(RetryRegistry retryRegistry) {
        return retryRegistry.retry("paymentGateway");
    }
}
