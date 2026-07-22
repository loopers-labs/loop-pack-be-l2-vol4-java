package com.loopers.interfaces.api.ranking;

import com.loopers.domain.ranking.RankingPeriod;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RankingV1DtoTest {

    @DisplayName("period 를 생략하면(null) 일간(DAILY)으로 해석한다 — 하위 호환.")
    @Test
    void defaultsToDaily_whenPeriodOmitted() {
        // when
        RankingPeriod period = RankingV1Dto.parsePeriod(null);

        // then
        assertThat(period).isEqualTo(RankingPeriod.DAILY);
    }

    @DisplayName("period=weekly 는 주간(WEEKLY)으로 해석한다.")
    @Test
    void parsesWeekly() {
        // when
        RankingPeriod period = RankingV1Dto.parsePeriod("weekly");

        // then
        assertThat(period).isEqualTo(RankingPeriod.WEEKLY);
    }

    @DisplayName("period=monthly 는 월간(MONTHLY)으로 해석한다.")
    @Test
    void parsesMonthly() {
        // when
        RankingPeriod period = RankingV1Dto.parsePeriod("monthly");

        // then
        assertThat(period).isEqualTo(RankingPeriod.MONTHLY);
    }

    @DisplayName("정의되지 않은 period 는 인터페이스 경계에서 400(CoreException)으로 막는다.")
    @Test
    void rejectsUnknownPeriod() {
        // when & then
        assertThatThrownBy(() -> RankingV1Dto.parsePeriod("yearly"))
            .isInstanceOf(CoreException.class);
    }
}
