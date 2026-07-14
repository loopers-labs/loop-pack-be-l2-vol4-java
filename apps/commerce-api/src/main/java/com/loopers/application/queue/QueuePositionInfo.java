package com.loopers.application.queue;

/**
 * 대기열 순번 조회 결과.
 *
 * @param status               WAITING(대기 중) / READY(입장 토큰 발급됨)
 * @param position             1-based 순번 (1 = 다음 입장 대상, READY면 null)
 * @param totalWaiting         전체 대기 인원 (READY면 null)
 * @param estimatedWaitSeconds 예상 대기 시간(초) (READY면 null)
 * @param retryAfterSeconds    권장 폴링 주기(초) (READY면 null — 더 이상 폴링 불필요)
 * @param token                입장 토큰 (WAITING이면 null)
 */
public record QueuePositionInfo(
    QueueStatus status,
    Long position,
    Long totalWaiting,
    Long estimatedWaitSeconds,
    Long retryAfterSeconds,
    String token
) {
    public enum QueueStatus { WAITING, READY }

    public static QueuePositionInfo waiting(long zeroBasedRank, long totalWaiting) {
        long position = zeroBasedRank + 1;
        return new QueuePositionInfo(
            QueueStatus.WAITING,
            position,
            totalWaiting,
            QueuePolicy.estimateWaitSeconds(position),
            QueuePolicy.RETRY_AFTER_SECONDS,
            null
        );
    }

    public static QueuePositionInfo ready(String token) {
        return new QueuePositionInfo(QueueStatus.READY, null, null, null, null, token);
    }
}
