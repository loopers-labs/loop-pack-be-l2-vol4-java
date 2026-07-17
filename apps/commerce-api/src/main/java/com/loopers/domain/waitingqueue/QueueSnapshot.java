package com.loopers.domain.waitingqueue;

/**
 * 대기열 조회 결과(도메인). rank는 1-based 사용자 표시값(대기열에 없으면 null).
 * aheadCount = 앞선 인원(=rank-1). estimatedWaitSeconds = 예상 대기 시간(초).
 */
public record QueueSnapshot(
    QueueStatus status,
    Long rank,
    long aheadCount,
    long estimatedWaitSeconds
) {
    public static QueueSnapshot ready() {
        return new QueueSnapshot(QueueStatus.READY, null, 0L, 0L);
    }

    public static QueueSnapshot notInQueue() {
        return new QueueSnapshot(QueueStatus.NOT_IN_QUEUE, null, 0L, 0L);
    }

    public static QueueSnapshot waiting(long rank, long aheadCount, long estimatedWaitSeconds) {
        return new QueueSnapshot(QueueStatus.WAITING, rank, aheadCount, estimatedWaitSeconds);
    }
}
