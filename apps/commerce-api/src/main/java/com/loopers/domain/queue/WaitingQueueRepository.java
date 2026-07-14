package com.loopers.domain.queue;

import java.util.List;
import java.util.Optional;

/**
 * 주문 API 앞단 대기열 저장소.
 * 진입 순서 보장과 중복 진입 방지는 구현체가 책임진다.
 */
public interface WaitingQueueRepository {

    /**
     * 대기열에 진입한다. 이미 진입한 유저면 기존 순번을 유지한다.
     *
     * @return 신규 진입이면 true, 이미 대기 중이면 false
     */
    boolean enter(Long userId, long enteredAtMillis);

    /** 대기열 내 0-based 순위. 대기 중이 아니면 empty. */
    Optional<Long> findRank(Long userId);

    /** 전체 대기 인원 수 */
    long countWaiting();

    /** 대기열에서 제거한다. */
    void remove(Long userId);

    /** 진입 순서상 가장 앞선 최대 {@code count}명을 대기열에서 원자적으로 꺼낸다(제거 후 반환). */
    List<Long> popMin(int count);
}
