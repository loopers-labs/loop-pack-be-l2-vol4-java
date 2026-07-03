package com.loopers.domain.outbox;

/**
 * Outbox 발행 상태.
 * <p>
 * 의도적으로 2상태만 둔다: 발행 실패는 예외 → 트랜잭션 롤백으로 PENDING을 유지해 다음 폴링에 재시도한다
 * (일시적 브로커 장애에 강함, at-least-once).
 * <p>
 * 한계(학습 범위상 미구현): 영구 실패(poison message)는 {@code ORDER BY id} 특성상 큐 앞을 계속 막아
 * head-of-line 블로킹을 일으키고, 배치 롤백으로 이미 성공한 앞 행이 재전송될 수 있다. 견고화하려면
 * {@code FAILED} 상태 + retryCount(임계치 초과 시 제외) 또는 DLQ가 필요하다.
 */
public enum OutboxStatus {
    PENDING,
    PUBLISHED
}
