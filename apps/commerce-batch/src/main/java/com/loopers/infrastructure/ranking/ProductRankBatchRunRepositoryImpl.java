package com.loopers.infrastructure.ranking;

import com.loopers.application.ranking.ProductRankBatchRunRepository;
import com.loopers.domain.ranking.BatchRunStatus;
import com.loopers.domain.ranking.ProductRankBatchRun;
import com.loopers.domain.ranking.RankingPeriod;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ProductRankBatchRunRepositoryImpl implements ProductRankBatchRunRepository {

    private final ProductRankBatchRunJpaRepository productRankBatchRunJpaRepository;

    @Override
    public ProductRankBatchRun create(RankingPeriod period, LocalDate rankStartDate, LocalDate rankEndDate) {
        ProductRankBatchRun batchRun = ProductRankBatchRun.create(period, rankStartDate, rankEndDate);
        return productRankBatchRunJpaRepository.save(ProductRankBatchRunJpaEntity.from(batchRun))
            .toDomain();
    }

    @Override
    public Optional<ProductRankBatchRun> findById(Long batchRunId) {
        return productRankBatchRunJpaRepository.findById(batchRunId)
            .map(ProductRankBatchRunJpaEntity::toDomain);
    }

    @Override
    public void markCompleted(Long batchRunId) {
        productRankBatchRunJpaRepository.updateStatus(batchRunId, BatchRunStatus.COMPLETED);
    }

    @Override
    public void markFailed(Long batchRunId) {
        productRankBatchRunJpaRepository.updateStatus(batchRunId, BatchRunStatus.FAILED);
    }
}
