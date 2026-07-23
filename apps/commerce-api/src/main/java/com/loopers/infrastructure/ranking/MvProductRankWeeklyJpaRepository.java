package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.MvProductRankWeeklyModel;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface MvProductRankWeeklyJpaRepository extends JpaRepository<MvProductRankWeeklyModel, Long> {

    List<MvProductRankWeeklyModel> findByWeekStartDate(LocalDate weekStartDate, Pageable pageable);

    long countByWeekStartDate(LocalDate weekStartDate);
}
