package com.loopers.application.queue;

public record QueueInfo(long position, long estimatedWaitSeconds, String token) {

    public static QueueInfo waiting(long position, long estimatedWaitSeconds) {
        return new QueueInfo(position, estimatedWaitSeconds, null);
    }

    public static QueueInfo ready(String token) {
        return new QueueInfo(0L, 0L, token);
    }
}
