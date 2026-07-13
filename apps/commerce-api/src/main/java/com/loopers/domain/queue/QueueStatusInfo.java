package com.loopers.domain.queue;

/**
 * 대기열 상태 조회 결과 — 애플리케이션/인터페이스 계층에 전달되는 DTO.
 *
 * <p>{@code status} 는 {@link QueueRedisStore.QueueStatus.State} 이름을 그대로 문자열로 옮긴 것이다.
 * {@code pollIntervalMillis} 는 ADMITTED 면 null(클라이언트는 폴링을 멈춰야 함),
 * WAITING 이면 순번에 따라 가까움/중간/멈 구간별로 다른 간격을 안내한다.
 */
public record QueueStatusInfo(String status, Long position, long waitingCount, Long pollIntervalMillis) {
}
