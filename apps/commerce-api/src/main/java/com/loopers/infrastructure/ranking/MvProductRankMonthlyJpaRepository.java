package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.MvProductRankMonthlyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MvProductRankMonthlyJpaRepository extends JpaRepository<MvProductRankMonthlyEntity, Long> {

    List<MvProductRankMonthlyEntity> findByPeriodMonthAndBatchIdOrderByRankingOrderAsc(int periodMonth, long batchId);
}
