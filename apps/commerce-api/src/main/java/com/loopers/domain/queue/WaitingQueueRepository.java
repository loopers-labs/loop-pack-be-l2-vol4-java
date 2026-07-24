package com.loopers.domain.queue;

import java.util.List;
import java.util.Optional;

/**
 * 대기열 저장소. Redis Sorted Set으로 구현된다.
 * <ul>
 *   <li>score = 진입 시각(timestamp), member = userId</li>
 *   <li>중복 member는 Set 특성으로 자동 방지된다</li>
 * </ul>
 */
public interface WaitingQueueRepository {

    /**
     * 대기열에 진입시킨다. 이미 존재하는 유저면 score를 덮어쓰지 않고 무시한다(순번 유지).
     *
     * @return 신규 진입이면 true, 이미 있던 유저면 false
     */
    boolean add(Long userId, double score);

    /** 0-based 순번. 대기열에 없으면 empty. */
    Optional<Long> rank(Long userId);

    /** 현재 대기열 전체 인원. */
    long size();

    /**
     * 대기열 앞에서 최대 {@code count}명을 원자적으로 꺼낸다(ZPOPMIN).
     * score가 낮은(먼저 진입한) 순서대로 반환하며, 반환된 member는 큐에서 제거된다.
     *
     * @return 꺼낸 userId 목록(진입 순서). 대기열이 비었으면 빈 목록.
     */
    List<Long> pollFront(long count);
}
