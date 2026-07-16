package com.loopers.domain.queue;

public record QueuePosition(
        long position,
        boolean admitted,
        String token,
        long estimatedWaitSeconds
) {
    /** 입장 토큰을 발급받아 대기열에서 빠진 유저. 순번 0, 대기시간 0, 토큰을 담아 바로 주문으로 진입한다. */
    public static QueuePosition admitted(String token) {
        return new QueuePosition(0L, true, token, 0L);
    }

    /** 아직 대기 중인 유저. 예상 대기 시간 = ceil(순번 / 초당 처리량). */
    public static QueuePosition waiting(long position, long throughputPerSecond) {
        long estimatedWaitSeconds = (long) Math.ceil((double) position / throughputPerSecond);
        return new QueuePosition(position, false, null, estimatedWaitSeconds);
    }
}