package com.loopers.domain.queue;

import java.util.List;
import java.util.Optional;

/**
 * 주문 대기열. 진입 순서를 보관하고(먼저 온 유저가 앞 순번), 스케줄러가 앞에서부터 꺼내 입장시킨다.
 * 같은 유저의 중복 진입은 저장소가 흡수한다(멱등).
 */
public interface WaitingQueueRepository {

    /**
     * 대기열에 진입한다. 이미 대기 중이면 기존 진입 시각을 보존한다 —
     * 진입 시각이 갱신되면 순번이 뒤로 밀리므로, 재진입은 아무것도 바꾸지 않아야 멱등이다.
     *
     * @return 신규 진입이면 true, 이미 대기 중이었으면 false
     */
    boolean enter(Long userId, long enteredAtMillis);

    /** 0-based 대기 순번. 대기열에 없으면 empty. */
    Optional<Long> rank(Long userId);

    /** 전체 대기 인원. */
    long size();

    /** 앞에서부터 최대 {@code count}명을 꺼낸다(대기열에서 제거). 진입 순서 그대로 반환한다. */
    List<Long> popFront(int count);
}
