package com.loopers.ranking.infrastructure;

import com.loopers.ranking.domain.WeeklyProductRankModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WeeklyProductRankJpaRepository extends JpaRepository<WeeklyProductRankModel, Long> {
    List<WeeklyProductRankModel> findAllByOrderByRankAsc();
}
