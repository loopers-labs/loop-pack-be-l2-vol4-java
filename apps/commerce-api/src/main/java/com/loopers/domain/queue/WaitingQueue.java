package com.loopers.domain.queue;

import java.util.List;
import java.util.Optional;

public interface WaitingQueue {  // 대기열(진입 순서 보장·중복 방지)의 도메인 포트. Redis Sorted Set 어댑터로 구현된다.

    /** 대기열 진입. 이미 있는 유저는 최초 순번(score)을 보존한다(ZADD NX). */
    void enter(String loginId);

    /** 0-based 순번. 대기열에 없으면 비어 있음. */
    Optional<Long> rank(String loginId);

    /** 전체 대기 인원(ZCARD). */
    long size();

    /** 앞에서 n 명을 진입 순서대로 꺼내 제거한다(ZPOPMIN). */
    List<String> pollFirst(int n);
}