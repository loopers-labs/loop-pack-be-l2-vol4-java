package com.loopers.interfaces.api.queue;

import com.loopers.application.queue.QueueInfo;

public class QueueV1Dto {

    public record EnterResponse(
            Long position
    ) {
        public static EnterResponse from(QueueInfo info) {
            return new EnterResponse(info.position());
        }
    }

    public record PositionResponse(
            Long position,
            Long totalWaiting,
            Long estimatedWaitSeconds,
            String token
    ) {
        public static PositionResponse from(QueueInfo info) {
            return new PositionResponse(info.position(), info.totalWaiting(), info.estimatedWaitSeconds(), info.token());
        }
    }
}
