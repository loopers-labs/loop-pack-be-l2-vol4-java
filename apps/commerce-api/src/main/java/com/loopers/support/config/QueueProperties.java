package com.loopers.support.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 주문 대기열 설정. 대기열은 행사 때만 켜는 장치이므로 {@code enabled} 토글을 둔다.
 *
 * <p>발급 속도(배치 크기·주기)는 하류에서 가장 좁은 병목의 용량에서 역산한다 — 커넥션 풀 기준
 * (풀 크기 ÷ 주문 1건 점유 시간 × 안전마진)보다 재고 핫로우 락 직렬화가 먼저 상한이 될 수 있고,
 * 행사 상품 구성(핫로우 집중도)에 따라 달라지므로 코드 상수가 아닌 설정으로 외부화한다.</p>
 *
 * <p>support 에 두는 이유: 스케줄러(infrastructure)·응용·인터셉터(interfaces)가 함께 읽는
 * cross-cutting 설정이라, 특정 레이어에 두면 역방향 의존이 생긴다.</p>
 *
 * @param enabled         대기열 게이트 on/off (off 면 주문 API 는 토큰 없이 통과)
 * @param tokenTtlSeconds 입장 토큰 TTL (초)
 * @param scheduler       토큰 발급 스케줄러 설정
 * @param polling         순번 구간별 폴링 주기 밴드 (position 오름차순)
 */
@ConfigurationProperties(prefix = "queue")
public record QueueProperties(
        boolean enabled,
        long tokenTtlSeconds,
        Scheduler scheduler,
        Polling polling
) {

    /**
     * @param intervalMs 발급 주기(ms) — 같은 발급 TPS 라도 간격이 크면 Thundering Herd (100ms 분산 발급)
     * @param batchSize  주기당 발급 인원
     */
    public record Scheduler(long intervalMs, int batchSize) {

        /** 발급 TPS. 예상 대기 시간 계산에 쓰며, 별도 설정이 아닌 유도값으로 둬 설정 간 drift 를 막는다. */
        public double issueRatePerSecond() {
            return batchSize * (1000.0 / intervalMs);
        }
    }

    /**
     * @param bands             순번 상한(maxPosition 이하) → 폴링 주기(ms). position 오름차순으로 평가
     * @param defaultIntervalMs 어떤 밴드에도 안 걸리는 뒷순번의 폴링 주기(ms)
     */
    public record Polling(List<Band> bands, long defaultIntervalMs) {

        public record Band(long maxPosition, long intervalMs) {
        }
    }
}
