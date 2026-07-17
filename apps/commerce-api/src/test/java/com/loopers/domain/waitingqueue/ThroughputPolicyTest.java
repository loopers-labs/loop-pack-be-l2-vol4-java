package com.loopers.domain.waitingqueue;

import com.loopers.config.waitingqueue.WaitingQueueProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ThroughputPolicy 산정 규칙 검증(방류형, docs/week8 §NFR-4·D2).
 * releaseSize(N)=30, interval(M)=2 기준 → throughput = N/M = 15/s.
 */
class ThroughputPolicyTest {

    private ThroughputPolicy policy(int releaseSize, int intervalSeconds) {
        WaitingQueueProperties props = new WaitingQueueProperties(
            true, releaseSize, intervalSeconds, 30, 2, 500);
        return new ThroughputPolicy(props);
    }

    @Test
    @DisplayName("releaseSize(N)·interval(M)·hardMax는 프로퍼티 그대로 노출한다")
    void knobs() {
        ThroughputPolicy p = policy(30, 2);
        assertThat(p.releaseSize()).isEqualTo(30);
        assertThat(p.releaseIntervalSeconds()).isEqualTo(2);
        assertThat(p.hardMaxActive()).isEqualTo(500);
    }

    @Test
    @DisplayName("throughput = releaseSize / interval (N명/M초)")
    void throughput() {
        assertThat(policy(30, 2).throughputPerSecond()).isEqualTo(15.0);
        assertThat(policy(30, 1).throughputPerSecond()).isEqualTo(30.0);
        assertThat(policy(60, 3).throughputPerSecond()).isEqualTo(20.0);
    }

    @Test
    @DisplayName("estimatedWaitSeconds = ceil(ahead / throughput)")
    void eta() {
        ThroughputPolicy p = policy(30, 2); // throughput 15/s
        assertThat(p.estimatedWaitSeconds(0)).isEqualTo(0);
        assertThat(p.estimatedWaitSeconds(15)).isEqualTo(1);
        assertThat(p.estimatedWaitSeconds(16)).isEqualTo(2);   // ceil
        assertThat(p.estimatedWaitSeconds(150)).isEqualTo(10);
    }
}
