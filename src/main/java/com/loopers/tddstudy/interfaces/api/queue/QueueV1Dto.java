package com.loopers.tddstudy.interfaces.api.queue;

import com.loopers.tddstudy.application.queue.QueueStatus;

public class QueueV1Dto {
    public record StatusResponse(boolean inQueue, long position,
                                 long estimatedWaitSeconds, long totalWaiting, String token) {
        public static StatusResponse from(QueueStatus s) {
            return new StatusResponse(s.inQueue(), s.position(),
                    s.estimatedWaitSeconds(), s.totalWaiting(), s.token());
        }
    }
}
