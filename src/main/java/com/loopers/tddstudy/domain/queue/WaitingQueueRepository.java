package com.loopers.tddstudy.domain.queue;

import java.util.List;

public interface WaitingQueueRepository {
    boolean enqueue(Long userId, long timestamp);  // ZADD NX — 신규면 true
    Long getRank(Long userId);                      // ZRANK (0-based), 없으면 null
    long getTotalCount();                            // ZCARD
    List<Long> popMin(int count);                    // ZPOPMIN N → userId 목록
}
