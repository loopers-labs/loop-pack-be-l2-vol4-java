package com.loopers.interfaces.api.queue;

import com.loopers.application.queue.QueuePositionInfo;

public class QueueV1Dto {

    public record PositionResponse(
        QueuePositionInfo.QueueStatus status,
        Long position,
        Long totalWaiting,
        Long estimatedWaitSeconds,
        Long retryAfterSeconds,
        String token
    ) {
        public static PositionResponse from(QueuePositionInfo info) {
            return new PositionResponse(
                info.status(),
                info.position(),
                info.totalWaiting(),
                info.estimatedWaitSeconds(),
                info.retryAfterSeconds(),
                info.token()
            );
        }
    }
}
