package com.loopers.queue.application;

public class QueueResult {

    private QueueResult() {
    }

    /** 대기열 진입 결과. 부여받은 순번. */
    public record Enter(long position) {
    }

    /** 순번 조회 결과. 순번·예상 대기시간·다음 폴링 간격. */
    public record Position(long position, long estimatedWaitSeconds, long pollAfterMs) {
    }
}
