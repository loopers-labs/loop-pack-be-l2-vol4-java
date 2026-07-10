package com.loopers.domain.queue;

import java.time.Duration;
import java.util.List;

/**
 * Redis Sorted Set 기반 대기열 + 입장 토큰 저장소.
 *
 * <p>대기열 자체는 DB에 흔적을 남기지 않는다 — 대기/토큰 모두 Redis 휘발성 상태이며,
 * 재고라는 유일한 정답(source of truth)은 여전히 DB에 있다. 이 저장소는 "누구를 먼저
 * 들여보낼지"와 "지금 입장 가능한 사람인지"만 책임진다 — 재고가 있는지는 판단하지 않는다.
 * 그 판단은 전부 다운스트림(주문 견적, 결제 승인 직전 원자적 재고 차감)에 맡긴다.
 *
 * <h2>키 구조 (productId 단위)</h2>
 * <ul>
 *   <li>{@code queue:{productId}:waiting} — ZSET, member=userId, score=입장 요청 시각(ms). FIFO.</li>
 *   <li>{@code queue:{productId}:token:{userId}} — STRING("VALID"/"LOCKED"), 발급 시 설정한 TTL 유지.</li>
 * </ul>
 */
public interface QueueRedisStore {

    /**
     * 대기열 입장 요청. 이미 토큰을 보유(ADMITTED) 중이면 그대로, 그 외엔 대기열에 추가한다
     * (중복 호출은 기존 순위를 유지 — ZADD NX).
     */
    EnterResult enter(Long productId, Long userId, long nowMillis);

    /** 현재 상태 조회 — 폴링 응답용. WAITING 이면 1-based position 포함. */
    QueueStatus status(Long productId, Long userId);

    /** 대기열 앞에서부터(FIFO) 최대 {@code batchSize}명을 꺼낸다. 재고와는 무관하다. */
    List<Long> admitBatch(Long productId, int batchSize);

    /** admitBatch로 뽑힌 유저에게 토큰 발급 (VALID, TTL 부여). */
    void issueToken(Long productId, Long userId, Duration ttl);

    /** /orders 진입 시 호출 — 동시 중복 주문 생성을 막기 위해 VALID → LOCKED 로 잠근다. */
    TokenLockResult tryLock(Long productId, Long userId);

    /** 주문 생성 시도가 끝나면(성공/실패 무관) LOCKED → VALID 로 되돌린다. 이미 소비/만료됐으면 무시. */
    void unlock(Long productId, Long userId);

    /** 결제 성공 또는 확정 품절 시 토큰을 완전히 삭제한다. */
    void consumeToken(Long productId, Long userId);

    /** 브라우저 이탈 신호 — 아직 대기 중이면 대기열에서 제거한다(이미 입장했으면 아무 효과 없음). */
    void leave(Long productId, Long userId);

    enum EnterResult { WAITING, ADMITTED }

    enum TokenLockResult { OK, EXPIRED, BUSY }

    record QueueStatus(State state, Long position, long waitingCount) {
        public enum State { WAITING, ADMITTED }
    }
}
