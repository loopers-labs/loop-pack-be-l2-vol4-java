package com.loopers.queue.application;

public class QueueResult {

    private QueueResult() {
    }

    /** 대기열 진입 결과. 부여받은 순번. */
    public record Enter(long position) {
    }

    /** 순번 조회 결과. 입장했으면 token 이 채워지고, 대기 중이면 null. */
    public record Position(long position, long estimatedWaitSeconds, long pollAfterMs, String token) {
    }
}
