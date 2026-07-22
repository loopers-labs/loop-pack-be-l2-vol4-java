package com.loopers.infrastructure.ranking;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MvProductRankMonthlyJpaRepository extends JpaRepository<MvProductRankMonthly, MvProductRankMonthlyId> {

    List<MvProductRankMonthly> findByYearMonthOrderByRankNoAsc(String yearMonth, Pageable pageable);
}
