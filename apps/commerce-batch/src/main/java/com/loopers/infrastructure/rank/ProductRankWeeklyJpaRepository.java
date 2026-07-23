package com.loopers.infrastructure.rank;

import com.loopers.domain.rank.ProductRankWeeklyModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductRankWeeklyJpaRepository extends JpaRepository<ProductRankWeeklyModel, Long> {

    List<ProductRankWeeklyModel> findByPeriodKeyOrderByRankNo(String periodKey);

    long deleteByPeriodKey(String periodKey);
}