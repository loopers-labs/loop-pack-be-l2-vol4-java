package com.loopers.domain.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class RankingKeyGeneratorTest {

    @DisplayName("2026년 7월 17일은 ranking:all:20260717 로 변환된다.")
    @Test
    void dailyKey_formatsAsYyyyMMdd() {
        String key = RankingKeyGenerator.dailyKey(LocalDate.of(2026, 7, 17));

        assertThat(key).isEqualTo("ranking:all:20260717");
    }
}
