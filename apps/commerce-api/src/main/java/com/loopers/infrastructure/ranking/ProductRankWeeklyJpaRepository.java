package com.loopers.infrastructure.ranking;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductRankWeeklyJpaRepository extends JpaRepository<ProductRankWeeklyModel, Long> {

    List<ProductRankWeeklyModel> findByPeriodKeyOrderByRankNo(String periodKey, Pageable pageable);

    long countByPeriodKey(String periodKey);
}