package com.loopers.infrastructure.ranking.mv;

import com.loopers.domain.ranking.mv.ProductRankMvPublishRepository;
import com.loopers.domain.ranking.mv.ProductRankMvRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

@RequiredArgsConstructor
@Repository
public class ProductRankMvPublishRepositoryImpl implements ProductRankMvPublishRepository {

    private final MvProductRankWeeklyJpaRepository weeklyJpaRepository;
    private final MvProductRankMonthlyJpaRepository monthlyJpaRepository;

    @Override
    @Transactional
    public void replaceWeeklyPeriod(String periodKey, List<ProductRankMvRow> rows, ZonedDateTime publishedAt) {
        weeklyJpaRepository.deleteByPeriodKey(periodKey);
        List<MvProductRankWeeklyEntity> entities = rows.stream()
            .map(row -> new MvProductRankWeeklyEntity(periodKey, row.productId(), row.rank(), row.score(), publishedAt))
            .toList();
        weeklyJpaRepository.saveAll(entities);
    }

    @Override
    @Transactional
    public void replaceMonthlyPeriod(String periodKey, List<ProductRankMvRow> rows, ZonedDateTime publishedAt) {
        monthlyJpaRepository.deleteByPeriodKey(periodKey);
        List<MvProductRankMonthlyEntity> entities = rows.stream()
            .map(row -> new MvProductRankMonthlyEntity(periodKey, row.productId(), row.rank(), row.score(), publishedAt))
            .toList();
        monthlyJpaRepository.saveAll(entities);
    }
}
