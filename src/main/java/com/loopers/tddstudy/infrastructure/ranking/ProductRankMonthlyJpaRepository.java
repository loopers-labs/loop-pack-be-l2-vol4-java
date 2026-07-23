package com.loopers.tddstudy.infrastructure.ranking;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProductRankMonthlyJpaRepository extends JpaRepository<ProductRankMonthly, Long> {

    void deleteByPeriodKey(String periodKey);

    List<ProductRankMonthly> findByPeriodKeyOrderByRankNoAsc(String periodKey, Pageable pageable);
}
