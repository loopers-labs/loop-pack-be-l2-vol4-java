package com.loopers.infrastructure.ranking;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductRankMonthlyJpaRepository extends JpaRepository<ProductRankMonthlyModel, Long> {

    List<ProductRankMonthlyModel> findByPeriodKeyOrderByRankNo(String periodKey, Pageable pageable);

    long countByPeriodKey(String periodKey);
}