package com.loopers.domain.queue;

import java.util.List;
import java.util.Optional;

public interface QueueRepository {
    long enter(Long userId, long timestampMillis);
    Optional<Long> rank(Long userId);
    long size();
    List<Long> popMin(int count);
}
