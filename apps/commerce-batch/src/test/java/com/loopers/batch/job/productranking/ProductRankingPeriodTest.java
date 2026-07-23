package com.loopers.batch.job.productranking;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductRankingPeriodTest {

  @DisplayName("주간 집계는 기준일이 속한 주의 월요일부터 시작한다.")
  @Test
  void resolvesWeeklyStartDate() {
    LocalDate targetDate = LocalDate.of(2026, 7, 22);

    LocalDate startDate = ProductRankingPeriod.WEEKLY.startDate(targetDate);

    assertThat(startDate).isEqualTo(LocalDate.of(2026, 7, 20));
  }

  @DisplayName("기준일이 월요일이면 주간 시작일도 같은 날이다.")
  @Test
  void keepsMondayAsWeeklyStartDate() {
    LocalDate monday = LocalDate.of(2026, 7, 20);

    assertThat(ProductRankingPeriod.WEEKLY.startDate(monday)).isEqualTo(monday);
  }

  @DisplayName("월간 집계는 기준일이 속한 달의 1일부터 시작한다.")
  @Test
  void resolvesMonthlyStartDate() {
    LocalDate targetDate = LocalDate.of(2026, 7, 22);

    LocalDate startDate = ProductRankingPeriod.MONTHLY.startDate(targetDate);

    assertThat(startDate).isEqualTo(LocalDate.of(2026, 7, 1));
  }
}
