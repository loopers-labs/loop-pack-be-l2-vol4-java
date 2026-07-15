package com.loopers.interfaces.api.queue;

import com.loopers.application.queue.QueueInfo;

public class QueueDto {

    public record PositionResponse(long position, long estimatedWaitSeconds, String token) {
        public static PositionResponse from(QueueInfo info) {
            return new PositionResponse(info.position(), info.estimatedWaitSeconds(), info.token());
        }
    }
}
