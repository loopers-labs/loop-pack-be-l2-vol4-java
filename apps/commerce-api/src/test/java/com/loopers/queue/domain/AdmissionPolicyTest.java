package com.loopers.queue.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class AdmissionPolicyTest {

    @Test
    @DisplayName("예상 대기시간은 순번 / 처리량을 올림한 초이다")
    void givenRankAndTps_whenEstimatedWait_thenCeilSeconds() {
        Duration wait = AdmissionPolicy.estimatedWait(300L, 50.0);

        assertThat(wait).isEqualTo(Duration.ofSeconds(6));
    }

    @Test
    @DisplayName("폴링 간격은 순번 구간별로 길어진다 (<100:1s, <1000:3s, 그 이상:5s)")
    void givenRank_whenPollInterval_thenBanded() {
        assertAll(
                () -> assertThat(AdmissionPolicy.pollInterval(50L)).isEqualTo(Duration.ofSeconds(1)),
                () -> assertThat(AdmissionPolicy.pollInterval(500L)).isEqualTo(Duration.ofSeconds(3)),
                () -> assertThat(AdmissionPolicy.pollInterval(5000L)).isEqualTo(Duration.ofSeconds(5))
        );
    }
}
