package com.loopers.application.queue;

public record QueueInfo(Long position, Long totalWaiting, Long estimatedWaitSeconds, String token) {

    public static QueueInfo forEnter(Long position) {
        return new QueueInfo(position, null, null, null);
    }

    public static QueueInfo forPosition(Long position, Long totalWaiting, Long estimatedWaitSeconds, String token) {
        return new QueueInfo(position, totalWaiting, estimatedWaitSeconds, token);
    }
}
