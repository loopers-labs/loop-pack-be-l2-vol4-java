package com.loopers.ranking.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RankPeriodTest {

    @DisplayName("period 문자열은 대소문자 무관하게 파싱된다.")
    @Test
    void parsesIgnoringCase() {
        assertThat(RankPeriod.from("weekly")).isEqualTo(RankPeriod.WEEKLY);
        assertThat(RankPeriod.from("MONTHLY")).isEqualTo(RankPeriod.MONTHLY);
    }

    @DisplayName("지원하지 않는 period면 예외가 발생한다.")
    @Test
    void throws_whenUnsupported() {
        assertThatThrownBy(() -> RankPeriod.from("daily"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RankPeriod.from(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @DisplayName("WEEKLY는 주간 MV 엔티티를, MONTHLY는 월간 MV 엔티티를 생성한다.")
    @Test
    void createsMatchingEntity() {
        assertThat(RankPeriod.WEEKLY.createRank(1L, 1, 10.0))
            .isInstanceOf(WeeklyProductRankModel.class);
        assertThat(RankPeriod.MONTHLY.createRank(1L, 1, 10.0))
            .isInstanceOf(MonthlyProductRankModel.class);
    }
}
