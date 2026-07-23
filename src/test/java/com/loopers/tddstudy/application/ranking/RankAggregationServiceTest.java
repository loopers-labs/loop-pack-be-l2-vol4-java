package com.loopers.tddstudy.application.ranking;

import com.loopers.tddstudy.infrastructure.metrics.ProductMetricsDaily;
import com.loopers.tddstudy.infrastructure.metrics.ProductMetricsDailyJpaRepository;
import com.loopers.tddstudy.infrastructure.ranking.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(RankAggregationService.class)
class RankAggregationServiceTest {

    @Autowired RankAggregationService service;
    @Autowired ProductMetricsDailyJpaRepository dailyRepository;
    @Autowired ProductRankWeeklyJpaRepository weeklyRepository;
    @Autowired ProductRankMonthlyJpaRepository monthlyRepository;

    @Test
    void 직전_주의_데이터를_합산해_등수를_매긴다() {
        // 6/29(월)~7/5(일) 주간 데이터
        dailyRepository.save(new ProductMetricsDaily(1L, LocalDate.of(2026, 6, 29), 0, 10, 0)); // 7.0
        dailyRepository.save(new ProductMetricsDaily(2L, LocalDate.of(2026, 7, 1), 0, 5, 0));   // 3.5
        dailyRepository.save(new ProductMetricsDaily(3L, LocalDate.of(2026, 7, 5), 10, 0, 0));  // 2.0

        // 7/6(월) 기준 실행 → 직전 주 집계
        int count = service.aggregateWeekly(LocalDate.of(2026, 7, 6));

        assertThat(count).isEqualTo(3);

        List<ProductRankWeekly> ranks =
                weeklyRepository.findByPeriodKeyOrderByRankNoAsc("2026-W27", PageRequest.of(0, 10));

        assertThat(ranks).hasSize(3);
        assertThat(ranks.get(0).getProductId()).isEqualTo(1L);   // 1등
        assertThat(ranks.get(0).getRankNo()).isEqualTo(1);
        assertThat(ranks.get(1).getProductId()).isEqualTo(2L);   // 2등
        assertThat(ranks.get(2).getProductId()).isEqualTo(3L);   // 3등
    }

    @Test
    void 주_범위_밖의_데이터는_집계되지_않는다() {
        dailyRepository.save(new ProductMetricsDaily(1L, LocalDate.of(2026, 7, 1), 0, 10, 0));  // 주 안
        dailyRepository.save(new ProductMetricsDaily(2L, LocalDate.of(2026, 7, 6), 0, 99, 0));  // 이번 주(아직 안 끝남)

        service.aggregateWeekly(LocalDate.of(2026, 7, 6));

        List<ProductRankWeekly> ranks =
                weeklyRepository.findByPeriodKeyOrderByRankNoAsc("2026-W27", PageRequest.of(0, 10));

        assertThat(ranks).hasSize(1);
        assertThat(ranks.get(0).getProductId()).isEqualTo(1L);
    }

    @Test
    void 두_번_실행해도_랭킹이_중복되지_않는다() {
        dailyRepository.save(new ProductMetricsDaily(1L, LocalDate.of(2026, 7, 1), 0, 10, 0));

        service.aggregateWeekly(LocalDate.of(2026, 7, 6));
        service.aggregateWeekly(LocalDate.of(2026, 7, 6));   // 재실행

        assertThat(weeklyRepository.count()).isEqualTo(1);    // 2개로 안 늘어남
    }

    @Test
    void 직전_달의_데이터를_합산한다() {
        dailyRepository.save(new ProductMetricsDaily(1L, LocalDate.of(2026, 7, 10), 0, 10, 0));
        dailyRepository.save(new ProductMetricsDaily(2L, LocalDate.of(2026, 8, 2), 0, 99, 0));   // 8월(범위 밖)

        // 8/1 기준 → 7월 전체 집계
        service.aggregateMonthly(LocalDate.of(2026, 8, 1));

        List<ProductRankMonthly> ranks =
                monthlyRepository.findByPeriodKeyOrderByRankNoAsc("2026-07", PageRequest.of(0, 10));

        assertThat(ranks).hasSize(1);
        assertThat(ranks.get(0).getProductId()).isEqualTo(1L);
        assertThat(ranks.get(0).getRankNo()).isEqualTo(1);
    }
}
