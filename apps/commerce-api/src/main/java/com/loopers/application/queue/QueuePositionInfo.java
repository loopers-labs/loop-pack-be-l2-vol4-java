package com.loopers.application.queue;

public record QueuePositionInfo(
        long position,
        long waitingCount
) {
    public static QueuePositionInfo of(long position, long waitingCount) {
        return new QueuePositionInfo(position, waitingCount);
    }
}