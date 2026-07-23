package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.PeriodRankedProduct;
import com.loopers.domain.ranking.PeriodicRanking;
import com.loopers.domain.ranking.RankingPeriod;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
public class MvPeriodicRanking implements PeriodicRanking {  // 기간 랭킹 조회 포트의 MV 어댑터. 배치가 미리 매긴 rank_no 순서를 그대로 읽는다

    private final ProductRankWeeklyJpaRepository weeklyJpaRepository;
    private final ProductRankMonthlyJpaRepository monthlyJpaRepository;

    @Override
    public List<PeriodRankedProduct> page(RankingPeriod period, LocalDate date, int page, int size) {
        String periodKey = period.periodKey(date);
        Pageable pageable = PageRequest.of(page, size);
        List<? extends ProductRankMvView> rows = switch (period) {
            case WEEKLY -> weeklyJpaRepository.findByPeriodKeyOrderByRankNo(periodKey, pageable);
            case MONTHLY -> monthlyJpaRepository.findByPeriodKeyOrderByRankNo(periodKey, pageable);
            case DAILY -> throw notMaterialized();
        };

        return rows.stream()
                .map(row -> new PeriodRankedProduct(row.getRankNo(), row.getProductId(), row.getScore()))
                .toList();
    }

    @Override
    public long totalCount(RankingPeriod period, LocalDate date) {
        String periodKey = period.periodKey(date);
        return switch (period) {
            case WEEKLY -> weeklyJpaRepository.countByPeriodKey(periodKey);
            case MONTHLY -> monthlyJpaRepository.countByPeriodKey(periodKey);
            case DAILY -> throw notMaterialized();
        };
    }

    private CoreException notMaterialized() {
        return new CoreException(ErrorType.INTERNAL_ERROR, "일간 랭킹은 MV 가 아닌 실시간 랭킹에서 조회해야 합니다.");
    }
}
