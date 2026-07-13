package com.loopers.queue.domain;

import java.util.List;

public interface WaitingQueueRepository {

    /** 대기열에 유저를 넣는다. 이미 있으면 순번을 유지한다(멱등). 새로 추가되면 true. */
    boolean add(String userId, long score);

    /** 0-based 순번. 대기열에 없으면 null. */
    Long rank(String userId);

    /** 전체 대기 인원. */
    long size();

    /** 앞에서 count 명을 원자적으로 꺼낸다(ZPOPMIN). 순번 앞 순서대로 반환. */
    List<String> popFront(int count);
}
