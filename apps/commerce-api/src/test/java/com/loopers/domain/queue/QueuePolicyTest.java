package com.loopers.domain.queue;

import com.loopers.support.config.QueueProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QueuePolicyTest {

    // 100ms × 14명 = 140 TPS, 폴링 밴드: ~100→1s / ~1000→3s / 그 뒤→5s
    private final QueuePolicy policy = new QueuePolicy(new QueueProperties(
            true,
            300,
            new QueueProperties.Scheduler(100, 14),
            new QueueProperties.Polling(
                    List.of(
                            new QueueProperties.Polling.Band(100, 1000),
                            new QueueProperties.Polling.Band(1000, 3000)
                    ),
                    5000
            )
    ));

    @Nested
    @DisplayName("예상 대기 시간 — ceil(순번 / 발급 TPS)")
    class EstimatedWait {

        @ParameterizedTest(name = "순번 {0} → 약 {1}초")
        @CsvSource({
                "1, 1",      // 140 미만도 최소 1초 (ceil)
                "140, 1",    // 정확히 1초치
                "141, 2",    // 1초치 + 1명 → 올림
                "512, 4",    // 512 / 140 = 3.66 → 4
                "10000, 72", // 10000 / 140 = 71.4 → 72
        })
        void ceilsPositionOverIssueRate(long position, long expectedSeconds) {
            assertThat(policy.estimatedWaitSeconds(position)).isEqualTo(expectedSeconds);
        }
    }

    @Nested
    @DisplayName("폴링 주기 — 순번 구간별 밴드")
    class PollAfter {

        @ParameterizedTest(name = "순번 {0} → {1}ms 후 재조회")
        @CsvSource({
                "1, 1000",
                "100, 1000",  // 첫 밴드 상한 포함
                "101, 3000",
                "1000, 3000", // 둘째 밴드 상한 포함
                "1001, 5000", // 밴드 밖 → 기본 주기
                "99999, 5000",
        })
        void mapsPositionToBandInterval(long position, long expectedMillis) {
            assertThat(policy.pollAfterMillis(position)).isEqualTo(expectedMillis);
        }
    }
}
