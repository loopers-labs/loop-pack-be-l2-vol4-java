package com.loopers.infrastructure.productrank;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ProductRankMonthlyMvJpaRepository extends JpaRepository<ProductRankMonthlyMvJpaEntity, ProductRankMvId> {
    List<ProductRankMonthlyMvJpaEntity> findById_AsOfDate(LocalDate asOfDate, Pageable pageable);
    long countById_AsOfDate(LocalDate asOfDate);
}
