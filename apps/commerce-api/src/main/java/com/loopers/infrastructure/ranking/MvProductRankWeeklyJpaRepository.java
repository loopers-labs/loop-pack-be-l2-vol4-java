package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.MvProductRankWeekly;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MvProductRankWeeklyJpaRepository extends JpaRepository<MvProductRankWeekly, Long> {

    List<MvProductRankWeekly> findByPeriodKeyOrderByRankNoAsc(String periodKey, Pageable pageable);

    long countByPeriodKey(String periodKey);
}
