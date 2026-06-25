package com.loopers.infrastructure.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * PG 클라이언트 설정. base-url/callback-url 은 배포 환경별 값, connect/read timeout 은 "타임아웃".
 */
@ConfigurationProperties(prefix = "pg-client")
public record PgClientProperties(
    String baseUrl,
    String callbackUrl,
    Duration connectTimeout,
    Duration readTimeout
) {
}
