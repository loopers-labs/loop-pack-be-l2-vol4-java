package com.loopers.ranking.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class RankingDatePolicyTest {

    private final LocalDate today = LocalDate.of(2026, 7, 17);

    @DisplayName("오늘은 서빙 창(최근 2일) 안이다")
    @Test
    void today_isServable() {
        assertThat(RankingDatePolicy.isServable(LocalDate.of(2026, 7, 17), today)).isTrue();
    }

    @DisplayName("어제는 서빙 창 안이다")
    @Test
    void yesterday_isServable() {
        assertThat(RankingDatePolicy.isServable(LocalDate.of(2026, 7, 16), today)).isTrue();
    }

    @DisplayName("내일(미래)은 서빙 창 밖이다")
    @Test
    void tomorrow_isNotServable() {
        assertThat(RankingDatePolicy.isServable(LocalDate.of(2026, 7, 18), today)).isFalse();
    }

    @DisplayName("2일 전(어제보다 이전)은 서빙 창 밖이다")
    @Test
    void twoDaysAgo_isNotServable() {
        assertThat(RankingDatePolicy.isServable(LocalDate.of(2026, 7, 15), today)).isFalse();
    }
}
