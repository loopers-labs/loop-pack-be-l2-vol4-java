package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductMetricsModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ProductMetricsJpaRepository extends JpaRepository<ProductMetricsModel, UUID> {

    @Query("SELECT m FROM ProductMetricsModel m WHERE m.date BETWEEN :startDate AND :endDate")
    List<ProductMetricsModel> findAllByDateBetween(LocalDate startDate, LocalDate endDate);
}
