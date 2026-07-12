package com.loopers.interfaces.api.ordering.queue;

import com.loopers.application.ordering.queue.OrderQueueResult;
import com.loopers.domain.ordering.queue.OrderQueueStatus;

public class OrderQueueDto {

    public record OrderQueueResponse(
        OrderQueueStatus status,
        Long position,
        long waitingCount,
        Long estimatedWaitSeconds,
        Long recommendedPollingIntervalSeconds,
        String token
    ) {
        public static OrderQueueResponse from(OrderQueueResult result) {
            return new OrderQueueResponse(
                result.status(),
                result.position(),
                result.waitingCount(),
                result.estimatedWaitSeconds(),
                result.recommendedPollingIntervalSeconds(),
                result.token()
            );
        }
    }
}
