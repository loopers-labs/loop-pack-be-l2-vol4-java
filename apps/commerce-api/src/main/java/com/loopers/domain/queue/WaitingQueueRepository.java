package com.loopers.domain.queue;

import java.util.List;
import java.util.Optional;

public interface WaitingQueueRepository {

    /**
     * 대기열에 진입시키고 현재 순번을 돌려준다.
     * 이미 대기 중인 유저의 재진입은 순번을 유지한다.
     */
    QueuePosition enroll(Long userId);

    /** 현재 순번 조회. 대기열에 없으면 빈 Optional. */
    Optional<QueuePosition> positionOf(Long userId);

    /** 전체 대기 인원. */
    long size();

    /** 대기열 앞에서 최대 count 명을 꺼내(제거) 진입 순서대로 반환한다. (스케줄러 입장 처리용) */
    List<Long> pollNext(int count);
}
