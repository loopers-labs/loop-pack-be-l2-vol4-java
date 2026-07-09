package com.loopers.domain.queue;

public interface WaitingQueueRepository {

    // ZADD 직후 같은 Master 템플릿으로 ZRANK까지 조회한 순번(rank)을 반환한다 — ZADD 원시 반환값이 아니다
    Long enter(Long userId, long timestampMillis);

    Long rank(Long userId);

    Long size();
}
