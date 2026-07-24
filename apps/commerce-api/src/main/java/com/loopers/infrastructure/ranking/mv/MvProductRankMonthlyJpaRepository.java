package com.loopers.infrastructure.ranking.mv;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MvProductRankMonthlyJpaRepository
    extends JpaRepository<MvProductRankMonthlyEntity, MvProductRankMonthlyEntity.MvProductRankMonthlyId> {

    List<MvProductRankMonthlyEntity> findByPeriodKeyOrderByRankPositionAsc(String periodKey, Pageable pageable);

    Optional<MvProductRankMonthlyEntity> findByPeriodKeyAndProductId(String periodKey, Long productId);

    long countByPeriodKey(String periodKey);
}
