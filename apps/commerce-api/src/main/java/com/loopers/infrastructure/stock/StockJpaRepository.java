package com.loopers.infrastructure.stock;

import com.loopers.domain.stock.model.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface StockJpaRepository extends JpaRepository<Stock, Long> {
    Optional<Stock> findByProductId(Long productId);
    List<Stock> findAllByProductIdIn(List<Long> productIds);

    @Modifying
    @Query("UPDATE Stock s SET s.deletedAt = :now WHERE s.productId IN :productIds AND s.deletedAt IS NULL")
    int softDeleteAllByProductIdIn(@Param("productIds") List<Long> productIds, @Param("now") ZonedDateTime now);
}
