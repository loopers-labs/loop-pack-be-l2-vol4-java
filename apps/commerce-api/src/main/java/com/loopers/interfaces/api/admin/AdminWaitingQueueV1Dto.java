package com.loopers.interfaces.api.admin;

import com.loopers.domain.waitingqueue.WaitingQueueStatusView;

public class AdminWaitingQueueV1Dto {

    public record StatusResponse(
        long queueSize,
        long activeCount,
        int releaseSize,
        int releaseIntervalSeconds,
        double throughputPerSecond,
        long estimatedTailWaitSeconds
    ) {
        public static StatusResponse from(WaitingQueueStatusView view) {
            return new StatusResponse(
                view.queueSize(),
                view.activeCount(),
                view.releaseSize(),
                view.releaseIntervalSeconds(),
                view.throughputPerSecond(),
                view.estimatedTailWaitSeconds()
            );
        }
    }
}
