package com.loopers.domain.queue;

public final class QueueThroughputPolicy {

    /** DB 커넥션 풀 40 / 평균 처리 200ms → 이론 최대 200 TPS, 안전 마진 70% 적용 */
    public static final int SAFE_TPS = 140;
    public static final int SCHEDULER_INTERVAL_MS = 100;
    public static final int BATCH_SIZE = SAFE_TPS / (1000 / SCHEDULER_INTERVAL_MS);

    private QueueThroughputPolicy() {
    }
}
