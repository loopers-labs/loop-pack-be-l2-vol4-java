package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.MvProductRankWeeklyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface MvProductRankWeeklyJpaRepository extends JpaRepository<MvProductRankWeeklyEntity, Long> {

    @Modifying
    @Query("DELETE FROM MvProductRankWeeklyEntity e WHERE e.yearWeek = :yearWeek AND e.batchId != :activeBatchId")
    void deleteOldBatches(int yearWeek, long activeBatchId);
}
