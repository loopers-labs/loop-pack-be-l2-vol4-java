package com.loopers.application.queue;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * 대기열 기능 설정값 — 게이트 대상 상품, 배치/토큰 정책, 폴링 간격.
 *
 * <p>{@code @Component} 를 붙이지 않는다 — 애플리케이션의 {@code @ConfigurationPropertiesScan}
 * 이 이미 이 레코드를 생성자 바인딩으로 등록한다. {@code @Component} 까지 더하면 컴포넌트 스캔이
 * 별도로 일반 빈 생성을 시도하면서 {@code @DefaultValue} 를 무시한 채 각 필드를 타입으로
 * 오토와이어하려 해 {@code No qualifying bean of type 'int'} 오류로 컨텍스트 로딩이 실패한다.
 */
@ConfigurationProperties(prefix = "queue")
public record QueueProperties(
    List<Long> targetProductIds,
    @DefaultValue("20") int batchSize,
    @DefaultValue("300") long tokenTtlSeconds,
    @DefaultValue("1000") long pollIntervalNearMillis,
    @DefaultValue("2000") long pollIntervalMidMillis,
    @DefaultValue("5000") long pollIntervalFarMillis
) {
    public QueueProperties {
        // Spring 생성자 바인딩은 프로퍼티가 아예 없을 때 null을 넘길 수 있다.
        targetProductIds = targetProductIds == null ? List.of() : targetProductIds;
    }

    public boolean isGated(Long productId) {
        return targetProductIds.contains(productId);
    }
}
