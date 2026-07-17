package com.loopers.domain.waitingqueue;

import com.loopers.config.waitingqueue.WaitingQueueProperties;
import org.springframework.stereotype.Component;

/**
 * 방류형(rate-based) 처리량·ETA 산정(docs/week8 §NFR-4·D2 코드화).
 *
 * <pre>
 * releaseSize (N)          = 한 주기 방류 인원(고정)
 * interval    (M)          = 방류 주기(초)
 * throughputPerSecond      = N / M                              // 초당 방류(입장) 인원
 * estimatedWaitSec(ahead)  = ceil(ahead / throughputPerSecond)  // 내 앞 인원이 빠지는 데 걸리는 시간
 * </pre>
 *
 * <p>정원제(활성 상한까지 리필)와 달리 방류량은 활성 점유량과 무관한 고정 레이트다.
 * 활성 상한 개념이 없으므로 다중 인스턴스 방류 총합은 {@link TokenIssuer}가 Redis 윈도우 레이트리밋으로 N/주기에 고정한다.
 */
@Component
public class ThroughputPolicy {

    private final WaitingQueueProperties props;

    public ThroughputPolicy(WaitingQueueProperties props) {
        this.props = props;
    }

    /** N: 한 주기에 방류할 인원. */
    public int releaseSize() {
        return props.releaseSize();
    }

    /** M: 방류 주기(초). */
    public int releaseIntervalSeconds() {
        return props.schedulerIntervalSeconds();
    }

    public double throughputPerSecond() {
        int m = props.schedulerIntervalSeconds();
        if (m <= 0) {
            return props.releaseSize();
        }
        return (double) props.releaseSize() / m;
    }

    public long estimatedWaitSeconds(long aheadCount) {
        double tps = throughputPerSecond();
        if (tps <= 0 || aheadCount <= 0) {
            return 0L;
        }
        return (long) Math.ceil(aheadCount / tps);
    }

    public int tokenTtlSeconds() {
        return props.tokenTtlSeconds();
    }

    /** 안전망 상한(0=비활성). 활성이 이 값에 도달하면 방류를 조인다(선택 B). */
    public int hardMaxActive() {
        return props.hardMaxActive();
    }

    /**
     * 권장 폴링 주기(초). 대기 인원이 많을수록 넓혀 총 폴링 QPS를 억제한다(NFR-7).
     * 1000명당 +1초, 1~10초로 클램프.
     */
    public int pollAfterSeconds(long aheadCount) {
        long scaled = 1 + Math.max(0L, aheadCount) / 1000;
        return (int) Math.min(10L, Math.max(1L, scaled));
    }
}
