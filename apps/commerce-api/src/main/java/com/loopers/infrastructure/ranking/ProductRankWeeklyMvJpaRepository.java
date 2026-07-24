package com.loopers.infrastructure.ranking;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ProductRankWeeklyMvJpaRepository extends JpaRepository<ProductRankWeeklyMvJpaEntity, Long> {

    List<ProductRankWeeklyMvJpaEntity> findByRankStartDateAndRankEndDateAndActiveTrueOrderByRankNoAsc(
        LocalDate rankStartDate,
        LocalDate rankEndDate,
        Pageable pageable
    );

    long countByRankStartDateAndRankEndDateAndActiveTrue(LocalDate rankStartDate, LocalDate rankEndDate);
}
