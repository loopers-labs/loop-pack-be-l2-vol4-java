package com.loopers.domain.ranking;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RankingCarryOverKeyPolicyTest {

  @DisplayName("서울 기준 실행 시각의 시간 키와 다음 날 일간·마커 키를 만든다.")
  @Test
  void createsSourceAndNextDayKeysInSeoul() {
    RankingCarryOverKeys keys =
        RankingCarryOverKeyPolicy.keys(Instant.parse("2026-07-16T14:50:00Z"));

    assertThat(keys.sourceHourlyKey()).isEqualTo("ranking:hour:2026071623");
    assertThat(keys.targetDailyKey()).isEqualTo("ranking:all:20260717");
    assertThat(keys.markerKey()).isEqualTo("ranking:carry-over:20260717");
  }
}
