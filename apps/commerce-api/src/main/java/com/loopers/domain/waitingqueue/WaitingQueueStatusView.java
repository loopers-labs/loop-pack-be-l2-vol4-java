package com.loopers.domain.waitingqueue;

/**
 * 대기열 운영 현황(Admin·관측용, FR-7·D8).
 */
public record WaitingQueueStatusView(
    long queueSize,
    long activeCount,
    int releaseSize,
    int releaseIntervalSeconds,
    double throughputPerSecond,
    long estimatedTailWaitSeconds
) {
}
