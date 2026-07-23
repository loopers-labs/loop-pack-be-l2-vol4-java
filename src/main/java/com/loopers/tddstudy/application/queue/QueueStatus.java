package com.loopers.tddstudy.application.queue;

public record QueueStatus(
        boolean inQueue,
        long position,               // 1-based 표시 순번
        long estimatedWaitSeconds,
        long totalWaiting,
        String token                 // 아직 미발급(Step 2에서 채움)
) {
    public static QueueStatus notInQueue(long total) {
        return new QueueStatus(false, 0, 0, total, null);
    }
}
