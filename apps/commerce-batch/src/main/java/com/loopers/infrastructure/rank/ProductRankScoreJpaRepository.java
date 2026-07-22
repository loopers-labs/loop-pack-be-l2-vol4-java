package com.loopers.infrastructure.rank;

import com.loopers.domain.rank.ProductRankScoreId;
import com.loopers.domain.rank.ProductRankScoreModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductRankScoreJpaRepository extends JpaRepository<ProductRankScoreModel, ProductRankScoreId> {

    List<ProductRankScoreModel> findTop100ByPeriodKeyOrderByScoreDesc(String periodKey);

    long deleteByPeriodKey(String periodKey);
}