package com.loopers.application.queue;

import com.loopers.domain.queue.QueuePosition;

public record QueuePositionInfo(
        long position,
        long waitingCount,
        long estimatedWaitSeconds,
        String token,
        boolean admitted
) {
    public static QueuePositionInfo from(QueuePosition status, long waitingCount) {
        return new QueuePositionInfo(
                status.position(),
                waitingCount,
                status.estimatedWaitSeconds(),
                status.token(),
                status.admitted()
        );
    }
}