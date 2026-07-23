package com.loopers.tddstudy.application.ranking;

import com.loopers.tddstudy.domain.ranking.RankingPeriod;
import com.loopers.tddstudy.domain.ranking.RankingPolicy;
import com.loopers.tddstudy.infrastructure.metrics.ProductMetricsDailyJpaRepository;
import com.loopers.tddstudy.infrastructure.metrics.ProductScoreSum;
import com.loopers.tddstudy.infrastructure.ranking.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class RankAggregationService {

    private static final int TOP_SIZE = 100;

    private final ProductMetricsDailyJpaRepository dailyRepository;
    private final ProductRankWeeklyJpaRepository weeklyRepository;
    private final ProductRankMonthlyJpaRepository monthlyRepository;

    public RankAggregationService(ProductMetricsDailyJpaRepository dailyRepository,
                                  ProductRankWeeklyJpaRepository weeklyRepository,
                                  ProductRankMonthlyJpaRepository monthlyRepository) {
        this.dailyRepository = dailyRepository;
        this.weeklyRepository = weeklyRepository;
        this.monthlyRepository = monthlyRepository;
    }

    @Transactional
    public int aggregateWeekly(LocalDate baseDate) {
        RankingPeriod period = RankingPeriod.lastCompletedWeek(baseDate);
        weeklyRepository.deleteByPeriodKey(period.key());
        weeklyRepository.flush();   // INSERT보다 DELETE가 먼저 나가도록 강제

        List<ProductRankWeekly> rows = new ArrayList<>();
        int rank = 1;
        for (ProductScoreSum sum : loadTopScores(period)) {
            rows.add(new ProductRankWeekly(period.key(), rank++, sum.productId(),
                    sum.score(), sum.likeSum(), sum.salesSum(), sum.viewSum()));
        }
        weeklyRepository.saveAll(rows);
        return rows.size();
    }

    @Transactional
    public int aggregateMonthly(LocalDate baseDate) {
        RankingPeriod period = RankingPeriod.lastCompletedMonth(baseDate);
        monthlyRepository.deleteByPeriodKey(period.key());
        monthlyRepository.flush();   // INSERT보다 DELETE가 먼저 나가도록 강제

        List<ProductRankMonthly> rows = new ArrayList<>();
        int rank = 1;
        for (ProductScoreSum sum : loadTopScores(period)) {
            rows.add(new ProductRankMonthly(period.key(), rank++, sum.productId(),
                    sum.score(), sum.likeSum(), sum.salesSum(), sum.viewSum()));
        }
        monthlyRepository.saveAll(rows);
        return rows.size();
    }

    private List<ProductScoreSum> loadTopScores(RankingPeriod period) {
        return dailyRepository.aggregateTopScores(
                period.startDate(), period.endDate(),
                RankingPolicy.likeWeight(), RankingPolicy.salesWeight(),
                PageRequest.of(0, TOP_SIZE));
    }
}
