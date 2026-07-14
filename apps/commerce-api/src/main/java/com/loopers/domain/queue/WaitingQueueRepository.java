package com.loopers.domain.queue;

import java.util.List;
import java.util.Optional;

/**
 * 주문 대기열 seam 인터페이스. 구현은 infrastructure(Redis ZSET) 에 둔다(DIP).
 * 순번은 진입 시각(score) 오름차순 — 재진입해도 줄 뒤로 밀리지 않아야 한다(NX).
 * 유저 식별자는 loginId(String) — 이 코드베이스에서 userId 는 Long PK 를 뜻하므로 혼용하지 않는다.
 */
public interface WaitingQueueRepository {

    /**
     * 대기열 진입(ZADD NX). 이미 대기 중이면 기존 score 를 유지한다 — 재진입 시 줄 뒤로 밀리는 버그 방지.
     *
     * @return 신규 진입이면 true, 이미 대기 중이었으면 false
     */
    boolean enqueue(String loginId, long enteredAtMillis);

    /** 현재 순번(0-based rank, ZRANK). 대기 중이 아니면 empty. */
    Optional<Long> findRank(String loginId);

    /** 전체 대기 인원(ZCARD). */
    long countWaiting();

    /** 맨 앞에서 최대 count 명을 제거하지 않고 조회한다(ZRANGE 0..count-1) — 입장 대상 선정용. */
    List<String> peekNextBatch(int count);

    /** 지정한 유저들을 대기열에서 제거한다(ZREM) — 입장(토큰 발급) 완료자 정리용. */
    void remove(List<String> loginIds);
}
