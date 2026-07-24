package com.loopers.application.ranking;

import com.loopers.domain.ranking.ProductRankBatchRun;
import com.loopers.domain.ranking.RankingPeriod;

import java.time.LocalDate;
import java.util.Optional;

public interface ProductRankBatchRunRepository {
    ProductRankBatchRun create(RankingPeriod period, LocalDate rankStartDate, LocalDate rankEndDate);

    Optional<ProductRankBatchRun> findById(Long batchRunId);

    void markCompleted(Long batchRunId);

    void markFailed(Long batchRunId);
}
