package com.loopers.interfaces.api.queue;

import com.loopers.application.queue.QueueInfo;
import com.loopers.application.queue.QueuePositionInfo;

public class QueueV1Dto {

    public record EnterResponse(long position, long totalWaiting) {
        public static EnterResponse from(QueueInfo info) {
            return new EnterResponse(info.position(), info.totalWaiting());
        }
    }

    public record PositionResponse(long position, long totalWaiting, String estimatedWait, String token) {
        public static PositionResponse from(QueuePositionInfo info) {
            return new PositionResponse(info.position(), info.totalWaiting(), info.estimatedWait(), info.token());
        }
    }
}
