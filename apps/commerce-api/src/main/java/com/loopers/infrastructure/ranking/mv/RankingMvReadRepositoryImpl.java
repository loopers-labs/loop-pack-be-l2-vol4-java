package com.loopers.infrastructure.ranking.mv;

import com.loopers.domain.ranking.RankingItem;
import com.loopers.domain.ranking.RankingMvPeriod;
import com.loopers.domain.ranking.RankingMvReadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Repository
public class RankingMvReadRepositoryImpl implements RankingMvReadRepository {

    private final MvProductRankWeeklyJpaRepository weeklyJpaRepository;
    private final MvProductRankMonthlyJpaRepository monthlyJpaRepository;

    @Override
    public List<RankingItem> findPage(RankingMvPeriod period, String periodKey, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return switch (period) {
            case WEEKLY -> weeklyJpaRepository.findByPeriodKeyOrderByRankPositionAsc(periodKey, pageable).stream()
                .map(entity -> new RankingItem(entity.getProductId(), entity.getScore()))
                .toList();
            case MONTHLY -> monthlyJpaRepository.findByPeriodKeyOrderByRankPositionAsc(periodKey, pageable).stream()
                .map(entity -> new RankingItem(entity.getProductId(), entity.getScore()))
                .toList();
        };
    }

    @Override
    public Optional<Long> findRank(RankingMvPeriod period, String periodKey, Long productId) {
        return switch (period) {
            case WEEKLY -> weeklyJpaRepository.findByPeriodKeyAndProductId(periodKey, productId)
                .map(entity -> entity.getRankPosition().longValue());
            case MONTHLY -> monthlyJpaRepository.findByPeriodKeyAndProductId(periodKey, productId)
                .map(entity -> entity.getRankPosition().longValue());
        };
    }

    @Override
    public long countTotal(RankingMvPeriod period, String periodKey) {
        return switch (period) {
            case WEEKLY -> weeklyJpaRepository.countByPeriodKey(periodKey);
            case MONTHLY -> monthlyJpaRepository.countByPeriodKey(periodKey);
        };
    }
}
