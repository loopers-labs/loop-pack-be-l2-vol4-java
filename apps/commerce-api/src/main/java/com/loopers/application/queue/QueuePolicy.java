package com.loopers.application.queue;

import java.time.Duration;

/**
 * 대기열 처리량 정책 — 스케줄러(발급)와 순번 조회(예상 대기 시간 계산)가 같은 값을 공유해야 한다.
 * 배치 크기 산정 근거는 docs/dev-guide/week8/to-do-and-done-tasks.md 참고.
 */
public final class QueuePolicy {

    /** 스케줄러 주기당 입장시키는 인원 수 */
    public static final int BATCH_SIZE = 30;

    /** 토큰 발급 스케줄러 주기 */
    public static final long SCHEDULER_PERIOD_MILLIS = 3000;

    /** 입장 토큰 TTL */
    public static final Duration TOKEN_TTL = Duration.ofMinutes(5);

    /** 대기 중 클라이언트에게 권장하는 폴링 주기 — 스케줄러 주기와 동일 (더 자주 물어도 순번이 바뀌지 않음) */
    public static final long RETRY_AFTER_SECONDS = SCHEDULER_PERIOD_MILLIS / 1000;

    private QueuePolicy() {}

    /**
     * 예상 대기 시간(초) = 내 순번이 포함된 배치가 처리될 때까지의 스케줄러 주기 횟수 × 주기.
     * e.g. BATCH_SIZE=30, 주기 3초일 때 순번 1~30 → 3초, 31~60 → 6초.
     */
    public static long estimateWaitSeconds(long position) {
        long batchesAhead = (position + BATCH_SIZE - 1) / BATCH_SIZE;
        return batchesAhead * (SCHEDULER_PERIOD_MILLIS / 1000);
    }
}
