package com.loopers.infrastructure.ranking;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface ProductMetricsBatchJpaRepository extends JpaRepository<ProductMetricsBatchJpaEntity, Long> {

    Page<ProductMetricsBatchJpaEntity> findByMetricDateBetween(
        LocalDate rankStartDate,
        LocalDate rankEndDate,
        Pageable pageable
    );
}
