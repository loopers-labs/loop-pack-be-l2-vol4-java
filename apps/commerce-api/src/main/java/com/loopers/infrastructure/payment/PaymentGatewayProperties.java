package com.loopers.infrastructure.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "pg")
public record PaymentGatewayProperties(
        @NotBlank String url,
        @NotBlank String callbackUrl,
        @Positive int connectTimeoutMs,
        @Positive int requestReadTimeoutMs,
        @Positive int queryReadTimeoutMs
) {
}
