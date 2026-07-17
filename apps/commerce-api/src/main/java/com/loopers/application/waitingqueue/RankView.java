package com.loopers.application.waitingqueue;

import com.loopers.domain.waitingqueue.QueueSnapshot;
import com.loopers.domain.waitingqueue.QueueStatus;

/**
 * 순번 조회 응답 뷰(도메인 스냅샷 + 권장 폴링 주기). Step 3 부하 완화(NFR-7).
 */
public record RankView(
    QueueStatus status,
    Long rank,
    long aheadCount,
    long estimatedWaitSeconds,
    int pollAfterSeconds
) {
    public static RankView from(QueueSnapshot snapshot, int pollAfterSeconds) {
        return new RankView(
            snapshot.status(),
            snapshot.rank(),
            snapshot.aheadCount(),
            snapshot.estimatedWaitSeconds(),
            pollAfterSeconds
        );
    }
}
