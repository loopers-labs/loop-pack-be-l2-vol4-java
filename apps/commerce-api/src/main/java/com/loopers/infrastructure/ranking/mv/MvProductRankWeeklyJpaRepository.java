package com.loopers.infrastructure.ranking.mv;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MvProductRankWeeklyJpaRepository
    extends JpaRepository<MvProductRankWeeklyEntity, MvProductRankWeeklyEntity.MvProductRankWeeklyId> {

    List<MvProductRankWeeklyEntity> findByPeriodKeyOrderByRankPositionAsc(String periodKey, Pageable pageable);

    Optional<MvProductRankWeeklyEntity> findByPeriodKeyAndProductId(String periodKey, Long productId);

    long countByPeriodKey(String periodKey);
}
