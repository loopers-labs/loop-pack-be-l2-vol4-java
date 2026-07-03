package com.loopers.infrastructure.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * payment-gateway.* 설정. callbackUrl 과 userId(X-USER-ID) 는 어댑터가 PG 호출 시 주입한다.
 */
@ConfigurationProperties(prefix = "payment-gateway")
public record PaymentGatewayProperties(
        String url,
        String callbackUrl,
        String userId
) {
}
