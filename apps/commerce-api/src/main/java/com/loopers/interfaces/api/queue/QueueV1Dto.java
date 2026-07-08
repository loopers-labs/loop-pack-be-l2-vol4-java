package com.loopers.interfaces.api.queue;

import com.loopers.application.queue.QueuePositionInfo;

public class QueueV1Dto {

    public record PositionResponse(
            long position,
            long waitingCount
    ) {
        public static PositionResponse from(QueuePositionInfo info) {
            return new PositionResponse(info.position(), info.waitingCount());
        }
    }
}