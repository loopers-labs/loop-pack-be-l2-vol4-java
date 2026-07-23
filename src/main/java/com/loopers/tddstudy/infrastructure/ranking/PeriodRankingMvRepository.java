package com.loopers.tddstudy.infrastructure.ranking;

import com.loopers.tddstudy.domain.ranking.PeriodRankingRepository;
import com.loopers.tddstudy.domain.ranking.RankingItem;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class PeriodRankingMvRepository implements PeriodRankingRepository {

    private final ProductRankWeeklyJpaRepository weeklyRepository;
    private final ProductRankMonthlyJpaRepository monthlyRepository;

    public PeriodRankingMvRepository(ProductRankWeeklyJpaRepository weeklyRepository,
                                     ProductRankMonthlyJpaRepository monthlyRepository) {
        this.weeklyRepository = weeklyRepository;
        this.monthlyRepository = monthlyRepository;
    }

    @Override
    public List<RankingItem> getWeeklyPage(String periodKey, int page, int size) {
        return weeklyRepository.findByPeriodKeyOrderByRankNoAsc(periodKey, pageOf(page, size))
                .stream()
                .map(row -> new RankingItem(row.getProductId(), row.getScore()))
                .toList();
    }

    @Override
    public List<RankingItem> getMonthlyPage(String periodKey, int page, int size) {
        return monthlyRepository.findByPeriodKeyOrderByRankNoAsc(periodKey, pageOf(page, size))
                .stream()
                .map(row -> new RankingItem(row.getProductId(), row.getScore()))
                .toList();
    }

    private Pageable pageOf(int page, int size) {
        return PageRequest.of(page - 1, size);   // API는 1부터, Spring은 0부터
    }
}
