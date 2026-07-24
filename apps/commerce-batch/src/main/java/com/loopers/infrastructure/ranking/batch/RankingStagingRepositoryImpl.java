package com.loopers.infrastructure.ranking.batch;

import com.loopers.domain.ranking.batch.RankingBatchPeriodType;
import com.loopers.domain.ranking.batch.RankingStagingRankRow;
import com.loopers.domain.ranking.batch.RankingStagingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Repository
public class RankingStagingRepositoryImpl implements RankingStagingRepository {

    private final MvProductRankStagingJpaRepository jpaRepository;

    @Override
    @Transactional
    public void deleteByPeriod(RankingBatchPeriodType periodType, String periodKey) {
        jpaRepository.deleteByPeriodTypeAndPeriodKey(periodType.name(), periodKey);
    }

    @Override
    @Transactional
    public void saveAll(RankingBatchPeriodType periodType, String periodKey, List<RankingStagingRankRow> rows) {
        List<MvProductRankStagingEntity> entities = rows.stream()
            .map(row -> new MvProductRankStagingEntity(
                periodType.name(), periodKey, row.productId(), row.rank(), row.score()
            ))
            .toList();
        jpaRepository.saveAll(entities);
    }

    @Override
    public List<RankingStagingRankRow> findByPeriod(RankingBatchPeriodType periodType, String periodKey) {
        return jpaRepository.findByPeriodTypeAndPeriodKeyOrderByRankPositionAsc(periodType.name(), periodKey).stream()
            .map(entity -> new RankingStagingRankRow(entity.getRankPosition(), entity.getProductId(), entity.getScore()))
            .toList();
    }
}
