package com.loopers.config.ratelimit;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * 주문 API 레이트리밋 설정값.
 *
 * <p>{@code @Validated} + {@code @Min(1)}로 기동 시점에 검증한다 — limit/windowSeconds가 0 이하면
 * {@code RateLimitRedisStoreImpl}의 윈도우 계산(0으로 나누기)이나 판정 자체가 무의미해지므로,
 * 잘못된 설정으로 애플리케이션이 뜬 채 조용히 오작동하는 대신 기동 자체를 막는다(fail-fast).
 *
 * <p>{@code @Component}를 붙이지 않는다 — {@code QueueProperties}와 같은 이유로,
 * 애플리케이션의 {@code @ConfigurationPropertiesScan}이 생성자 바인딩으로 이미 등록한다.
 */
@Validated
@ConfigurationProperties(prefix = "ratelimit.orders")
public record RateLimitProperties(
    @Min(1) @DefaultValue("100") int limit,
    @Min(1) @DefaultValue("1") int windowSeconds
) {
}
