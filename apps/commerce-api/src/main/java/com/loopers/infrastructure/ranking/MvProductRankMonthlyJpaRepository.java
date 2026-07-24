package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.MvProductRankMonthlyModel;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface MvProductRankMonthlyJpaRepository extends JpaRepository<MvProductRankMonthlyModel, Long> {

    List<MvProductRankMonthlyModel> findByMonthStartDate(LocalDate monthStartDate, Pageable pageable);

    long countByMonthStartDate(LocalDate monthStartDate);
}
