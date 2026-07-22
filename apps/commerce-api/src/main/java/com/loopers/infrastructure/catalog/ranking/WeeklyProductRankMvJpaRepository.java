package com.loopers.infrastructure.catalog.ranking;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface WeeklyProductRankMvJpaRepository extends JpaRepository<WeeklyProductRankMvJpaEntity, Long> {

    List<WeeklyProductRankMvJpaEntity> findByPeriodStartDateAndPeriodEndDateOrderByRankAsc(
        LocalDate periodStartDate,
        LocalDate periodEndDate,
        Pageable pageable
    );

    long countByPeriodStartDateAndPeriodEndDate(LocalDate periodStartDate, LocalDate periodEndDate);
}
