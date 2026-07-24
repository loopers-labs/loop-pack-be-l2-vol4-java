package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.ProductRankMvRepository;
import com.loopers.domain.ranking.RankingEntry;
import com.loopers.domain.ranking.RankingPeriod;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@RequiredArgsConstructor
@Component
public class ProductRankMvRepositoryImpl implements ProductRankMvRepository {

    private final MvProductRankWeeklyJpaRepository weeklyJpaRepository;
    private final MvProductRankMonthlyJpaRepository monthlyJpaRepository;

    @Override
    public List<RankingEntry> findRankings(RankingPeriod period, LocalDate aggregateDate, int page, int size) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.ASC, "ranking"));
        return switch (period) {
            case WEEKLY -> weeklyJpaRepository.findByWeekStartDate(aggregateDate, pageable).stream()
                .map(m -> toEntry(m.getProductId(), m.getRanking(), m.getScore()))
                .toList();
            case MONTHLY -> monthlyJpaRepository.findByMonthStartDate(aggregateDate, pageable).stream()
                .map(m -> toEntry(m.getProductId(), m.getRanking(), m.getScore()))
                .toList();
            case DAILY -> throw new CoreException(ErrorType.BAD_REQUEST, "DAILY는 MV 조회 대상이 아닙니다.");
        };
    }

    @Override
    public long count(RankingPeriod period, LocalDate aggregateDate) {
        return switch (period) {
            case WEEKLY -> weeklyJpaRepository.countByWeekStartDate(aggregateDate);
            case MONTHLY -> monthlyJpaRepository.countByMonthStartDate(aggregateDate);
            case DAILY -> throw new CoreException(ErrorType.BAD_REQUEST, "DAILY는 MV 조회 대상이 아닙니다.");
        };
    }

    private static RankingEntry toEntry(Long productId, Integer ranking, Long score) {
        return new RankingEntry(productId, ranking.longValue(), score.doubleValue());
    }
}
