package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.MvProductRankMonthly;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MvProductRankMonthlyJpaRepository extends JpaRepository<MvProductRankMonthly, Long> {

    List<MvProductRankMonthly> findByPeriodKeyOrderByRankNoAsc(String periodKey, Pageable pageable);

    long countByPeriodKey(String periodKey);
}
