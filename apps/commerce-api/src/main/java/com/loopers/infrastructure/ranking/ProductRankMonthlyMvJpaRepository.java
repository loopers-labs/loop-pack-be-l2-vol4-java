package com.loopers.infrastructure.ranking;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ProductRankMonthlyMvJpaRepository extends JpaRepository<ProductRankMonthlyMvJpaEntity, Long> {

    List<ProductRankMonthlyMvJpaEntity> findByRankStartDateAndRankEndDateAndActiveTrueOrderByRankNoAsc(
        LocalDate rankStartDate,
        LocalDate rankEndDate,
        Pageable pageable
    );

    long countByRankStartDateAndRankEndDateAndActiveTrue(LocalDate rankStartDate, LocalDate rankEndDate);
}
