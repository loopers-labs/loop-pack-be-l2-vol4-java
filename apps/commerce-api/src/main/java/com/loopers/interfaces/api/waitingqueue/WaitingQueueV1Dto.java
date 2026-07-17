package com.loopers.interfaces.api.waitingqueue;

import com.loopers.application.waitingqueue.RankView;

public class WaitingQueueV1Dto {

    /**
     * 대기열 진입/순번 조회 응답.
     * pollAfterSeconds: 클라이언트 권장 폴링 주기(초). 대기 많을수록 확대(NFR-7). READY/미진입이면 0(폴링 중단).
     */
    public record RankResponse(
        String status,
        Long rank,
        long aheadCount,
        long estimatedWaitSeconds,
        int pollAfterSeconds
    ) {
        public static RankResponse from(RankView view) {
            return new RankResponse(
                view.status().name(),
                view.rank(),
                view.aheadCount(),
                view.estimatedWaitSeconds(),
                view.pollAfterSeconds()
            );
        }
    }
}
