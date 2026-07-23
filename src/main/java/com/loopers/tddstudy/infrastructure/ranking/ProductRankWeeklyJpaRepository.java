package com.loopers.tddstudy.infrastructure.ranking;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProductRankWeeklyJpaRepository extends JpaRepository<ProductRankWeekly, Long> {

    void deleteByPeriodKey(String periodKey);

    List<ProductRankWeekly> findByPeriodKeyOrderByRankNoAsc(String periodKey, Pageable pageable);
}
