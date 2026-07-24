package com.loopers.application.ranking;

import com.loopers.domain.ranking.ProductRankSnapshot;
import com.loopers.domain.ranking.RankingPeriod;

import java.time.LocalDate;
import java.util.List;

public interface ProductRankSnapshotRepository {
    void saveAll(List<ProductRankSnapshot> snapshots);

    boolean existsInvalidSnapshot(Long batchRunId);

    void deactivateActiveSnapshot(RankingPeriod period, LocalDate rankStartDate, LocalDate rankEndDate);

    void activateSnapshot(Long batchRunId);
}
