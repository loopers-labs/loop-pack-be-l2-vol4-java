package com.loopers.ranking.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RankingPeriodTest {

    @DisplayName("기간을 생략하거나 공백으로 전달하면 일간 랭킹으로 해석한다.")
    @Test
    void defaultsToDaily() {
        assertThat(RankingPeriod.from(null)).isEqualTo(RankingPeriod.DAILY);
        assertThat(RankingPeriod.from(" ")).isEqualTo(RankingPeriod.DAILY);
    }

    @DisplayName("기간은 대소문자와 앞뒤 공백에 관계없이 해석한다.")
    @Test
    void parsesSupportedPeriod() {
        assertThat(RankingPeriod.from("daily")).isEqualTo(RankingPeriod.DAILY);
        assertThat(RankingPeriod.from(" Weekly "))
                .isEqualTo(RankingPeriod.WEEKLY);
        assertThat(RankingPeriod.from("MONTHLY"))
                .isEqualTo(RankingPeriod.MONTHLY);
    }

    @DisplayName("지원하지 않는 기간은 BAD_REQUEST로 거절한다.")
    @Test
    void rejectsUnsupportedPeriod() {
        assertThatThrownBy(() -> RankingPeriod.from("yearly"))
                .isInstanceOfSatisfying(
                        CoreException.class,
                        exception ->
                                assertThat(exception.getErrorType())
                                        .isEqualTo(ErrorType.BAD_REQUEST));
    }
}
