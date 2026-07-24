package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.MvProductRank;
import com.loopers.domain.ranking.MvRankingRepository;
import com.loopers.domain.ranking.RankingPeriod;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class MvRankingRepositoryImpl implements MvRankingRepository {

    private final MvProductRankWeeklyJpaRepository weeklyRepository;
    private final MvProductRankMonthlyJpaRepository monthlyRepository;

    @Override
    public List<Long> topProductIds(RankingPeriod period, String periodKey, long offset, int size) {
        requireMvPeriod(period);
        Pageable pageable = PageRequest.of((int) (offset / size), size);
        List<? extends MvProductRank> rows = period == RankingPeriod.WEEKLY
                ? weeklyRepository.findByPeriodKeyOrderByRankNoAsc(periodKey, pageable)
                : monthlyRepository.findByPeriodKeyOrderByRankNoAsc(periodKey, pageable);
        return rows.stream().map(MvProductRank::getProductId).toList();
    }

    @Override
    public long size(RankingPeriod period, String periodKey) {
        requireMvPeriod(period);
        return period == RankingPeriod.WEEKLY
                ? weeklyRepository.countByPeriodKey(periodKey)
                : monthlyRepository.countByPeriodKey(periodKey);
    }

    /** MV는 주간·월간만 존재한다. 일간(Redis)·null이 흘러들어오면 조용히 월간으로 새지 않도록 막는다. */
    private void requireMvPeriod(RankingPeriod period) {
        if (period != RankingPeriod.WEEKLY && period != RankingPeriod.MONTHLY) {
            throw new IllegalArgumentException("MV 랭킹 조회는 WEEKLY/MONTHLY만 지원한다: " + period);
        }
    }
}
