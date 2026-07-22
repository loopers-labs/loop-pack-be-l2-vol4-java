package com.loopers.infrastructure.catalog.ranking;

import com.loopers.domain.catalog.ranking.ProductRankMvRepository;
import com.loopers.domain.catalog.ranking.RankingPeriod;
import com.loopers.domain.catalog.ranking.RankingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@RequiredArgsConstructor
@Repository
public class ProductRankMvRepositoryImpl implements ProductRankMvRepository {

    private final WeeklyProductRankMvJpaRepository weeklyProductRankMvJpaRepository;
    private final MonthlyProductRankMvJpaRepository monthlyProductRankMvJpaRepository;

    @Override
    public List<RankingRepository.Entry> findRankings(
        RankingPeriod period,
        LocalDate periodStartDate,
        LocalDate periodEndDate,
        int page,
        int size
    ) {
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = size <= 0 ? 20 : size;
        PageRequest pageable = PageRequest.of(normalizedPage, normalizedSize);

        return switch (period) {
            case WEEKLY -> weeklyProductRankMvJpaRepository
                .findByPeriodStartDateAndPeriodEndDateOrderByRankAsc(periodStartDate, periodEndDate, pageable)
                .stream()
                .map(ProductRankMvJpaEntity::toEntry)
                .toList();
            case MONTHLY -> monthlyProductRankMvJpaRepository
                .findByPeriodStartDateAndPeriodEndDateOrderByRankAsc(periodStartDate, periodEndDate, pageable)
                .stream()
                .map(ProductRankMvJpaEntity::toEntry)
                .toList();
            case DAILY -> List.of();
        };
    }

    @Override
    public long count(RankingPeriod period, LocalDate periodStartDate, LocalDate periodEndDate) {
        return switch (period) {
            case WEEKLY -> weeklyProductRankMvJpaRepository.countByPeriodStartDateAndPeriodEndDate(
                periodStartDate,
                periodEndDate
            );
            case MONTHLY -> monthlyProductRankMvJpaRepository.countByPeriodStartDateAndPeriodEndDate(
                periodStartDate,
                periodEndDate
            );
            case DAILY -> 0L;
        };
    }
}
