package com.loopers.job.catalog.ranking;

import com.loopers.batch.job.catalog.ranking.ProductRankingJobParameters;
import com.loopers.batch.job.catalog.ranking.ProductRankingPeriod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

class ProductRankingJobParametersTest {

    @DisplayName("weekly 파라미터는 baseDate가 속한 주의 월요일부터 일요일까지를 집계 기간으로 계산한다.")
    @Test
    void calculatesWeeklyRange() {
        // arrange
        String period = "weekly";
        String baseDate = "20260722";

        // act
        ProductRankingJobParameters parameters = ProductRankingJobParameters.of(period, baseDate);

        // assert
        assertAll(
            () -> assertThat(parameters.period()).isEqualTo(ProductRankingPeriod.WEEKLY),
            () -> assertThat(parameters.baseDate()).isEqualTo(LocalDate.of(2026, 7, 22)),
            () -> assertThat(parameters.range().startDate()).isEqualTo(LocalDate.of(2026, 7, 20)),
            () -> assertThat(parameters.range().endDate()).isEqualTo(LocalDate.of(2026, 7, 26))
        );
    }

    @DisplayName("monthly 파라미터는 baseDate가 속한 달의 1일부터 말일까지를 집계 기간으로 계산한다.")
    @Test
    void calculatesMonthlyRange() {
        // arrange
        String period = "monthly";
        String baseDate = "20260215";

        // act
        ProductRankingJobParameters parameters = ProductRankingJobParameters.of(period, baseDate);

        // assert
        assertAll(
            () -> assertThat(parameters.period()).isEqualTo(ProductRankingPeriod.MONTHLY),
            () -> assertThat(parameters.range().startDate()).isEqualTo(LocalDate.of(2026, 2, 1)),
            () -> assertThat(parameters.range().endDate()).isEqualTo(LocalDate.of(2026, 2, 28))
        );
    }

    @DisplayName("지원하지 않는 period나 잘못된 baseDate는 거부한다.")
    @Test
    void rejectsInvalidParameters() {
        // arrange
        String invalidPeriod = "daily";
        String invalidBaseDate = "2026-07-22";

        // act & assert
        assertAll(
            () -> assertThatThrownBy(() -> ProductRankingJobParameters.of(invalidPeriod, "20260722"))
                .isInstanceOf(IllegalArgumentException.class),
            () -> assertThatThrownBy(() -> ProductRankingJobParameters.of("weekly", invalidBaseDate))
                .isInstanceOf(IllegalArgumentException.class)
        );
    }
}
