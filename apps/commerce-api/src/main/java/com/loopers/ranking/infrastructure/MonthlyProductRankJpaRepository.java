package com.loopers.ranking.infrastructure;

import com.loopers.ranking.domain.MonthlyProductRankModel;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MonthlyProductRankJpaRepository extends JpaRepository<MonthlyProductRankModel, Long> {
    List<MonthlyProductRankModel> findAllByOrderByRankAsc(Pageable pageable);
}
