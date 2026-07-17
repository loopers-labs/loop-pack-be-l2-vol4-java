package com.loopers.config.waitingqueue;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 대기열(Virtual Waiting Room) 설정. 값의 근거는 docs/week8/01-requirements.md §9(D1·D2)·§NFR-4.
 *
 * <p><b>방류형(rate-based) 관문</b>: 매 {@code schedulerIntervalSeconds}(=M)초마다 대기열 앞에서
 * {@code releaseSize}(=N)명을 활성으로 방류한다. 활성 점유량과 무관하게 고정 레이트로 내보내며,
 * 순간 처리량 = N/M(초당). 모든 파생값(throughput/eta)은
 * {@link com.loopers.domain.waitingqueue.ThroughputPolicy}에서 이 프로퍼티로부터 계산한다.
 * 부하테스트 결과에 따라 코드 변경 없이 yml로 N·M을 튜닝한다.
 */
@ConfigurationProperties(prefix = "waiting-queue")
public record WaitingQueueProperties(
    /** 대기열 관문 활성화. false면 주문 API가 기존처럼 토큰 없이 동작(평시). true면 블랙프라이데이 관문 on. */
    @DefaultValue("false") boolean enabled,
    /** N: 한 주기에 방류할 인원. 매 주기 대기열 앞에서 최대 이만큼 활성으로 내보낸다. */
    @DefaultValue("30") int releaseSize,
    /** M: 방류 주기(초). 스케줄러 발화 간격이자 Redis 레이트리밋 윈도우 크기. throughput = N/M. */
    @DefaultValue("2") int schedulerIntervalSeconds,
    /** 입장 토큰 TTL(초, D1 개정 — TTL 스윕 시뮬레이션으로 60→30 조정, docs/week8/06). */
    @DefaultValue("30") int tokenTtlSeconds,
    /** 순번 조회 결과 캐시 TTL(초, D5). */
    @DefaultValue("2") int rankCacheSeconds,
    /**
     * 안전망 상한(선택 B). 활성 토큰 보유자 수가 이 값에 도달하면 그 주기 방류를 조인다(0=비활성=순수 방류형).
     * 방류형은 활성 gate가 없어, 처리 정체로 avgProcess가 나빠지면 활성이 비정상 급증할 수 있다. 이 상한은
     * 그 폭주만 막는 <b>비상 브레이크</b>이지 주 제어가 아니다 → 정상 footprint(≈ N/M×TTL)보다 넉넉히 잡는다.
     */
    @DefaultValue("500") int hardMaxActive
) {
}
