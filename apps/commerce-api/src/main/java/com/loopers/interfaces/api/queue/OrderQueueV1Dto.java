package com.loopers.interfaces.api.queue;

import com.loopers.application.queue.QueuePositionInfo;

public class OrderQueueV1Dto {

    public record QueuePositionResponse(long position, long totalWaiting, long estimatedWaitSeconds, String token) {
        public static QueuePositionResponse from(QueuePositionInfo info) {
            return new QueuePositionResponse(info.position(), info.totalWaiting(), info.estimatedWaitSeconds(), info.token());
        }
    }
}
