package com.loopers.application.ordering.queue;

import com.loopers.domain.ordering.queue.OrderQueueStatus;

public record OrderQueueResult(
    OrderQueueStatus status,
    Long position,
    long waitingCount,
    Long estimatedWaitSeconds,
    Long recommendedPollingIntervalSeconds,
    String token
) {
}
