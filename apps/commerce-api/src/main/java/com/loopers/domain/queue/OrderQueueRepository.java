package com.loopers.domain.queue;

import java.util.Optional;

public interface OrderQueueRepository {

    /** 대기열에 진입시킨다. 이미 있으면 기존 순번을 유지한다(중복 진입 무시). */
    void enter(Long userId, long score);

    /** 대기열에서의 0-based 순번. 대기열에 없으면 empty. */
    Optional<Long> findRank(Long userId);

    /** 전체 대기 인원. */
    long size();
}
