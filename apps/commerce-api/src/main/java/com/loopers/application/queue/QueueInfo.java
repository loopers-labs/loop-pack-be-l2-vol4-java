package com.loopers.application.queue;

/**
 * 대기열 상태 응답 Info.
 * 입장 완료(토큰 보유)면 position=0 + token, 대기 중이면 position=rank+1 + token=null.
 */
public record QueueInfo(
    long position,
    long waitingCount,
    long estimatedWaitSeconds,
    long suggestedPollIntervalSeconds,
    String token
) {

    /** 입장 완료 — 더 이상 대기·폴링이 필요 없다. */
    public static QueueInfo admitted(String token, long waitingCount) {
        return new QueueInfo(0, waitingCount, 0, 0, token);
    }

    public static QueueInfo waiting(
        long position, long waitingCount, long estimatedWaitSeconds, long suggestedPollIntervalSeconds
    ) {
        return new QueueInfo(position, waitingCount, estimatedWaitSeconds, suggestedPollIntervalSeconds, null);
    }
}
