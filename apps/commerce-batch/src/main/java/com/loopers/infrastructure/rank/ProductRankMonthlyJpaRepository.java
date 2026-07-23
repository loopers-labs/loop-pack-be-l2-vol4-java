package com.loopers.infrastructure.rank;

import com.loopers.domain.rank.ProductRankMonthlyModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductRankMonthlyJpaRepository extends JpaRepository<ProductRankMonthlyModel, Long> {

    List<ProductRankMonthlyModel> findByPeriodKeyOrderByRankNo(String periodKey);

    long deleteByPeriodKey(String periodKey);
}