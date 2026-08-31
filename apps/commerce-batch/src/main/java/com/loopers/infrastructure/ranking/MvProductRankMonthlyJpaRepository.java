package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.MvProductRankMonthlyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface MvProductRankMonthlyJpaRepository extends JpaRepository<MvProductRankMonthlyEntity, Long> {

    @Modifying
    @Query("DELETE FROM MvProductRankMonthlyEntity e WHERE e.periodMonth = :yearMonth AND e.batchId != :activeBatchId")
    void deleteOldBatches(int yearMonth, long activeBatchId);
}
