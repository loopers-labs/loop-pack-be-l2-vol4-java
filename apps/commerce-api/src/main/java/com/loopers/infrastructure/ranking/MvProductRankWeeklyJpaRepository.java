package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.MvProductRankWeeklyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MvProductRankWeeklyJpaRepository extends JpaRepository<MvProductRankWeeklyEntity, Long> {

    List<MvProductRankWeeklyEntity> findByYearWeekAndBatchIdOrderByRankingOrderAsc(int yearWeek, long batchId);
}
