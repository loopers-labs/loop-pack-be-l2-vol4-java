package com.loopers.infrastructure.stock;

import com.loopers.domain.stock.StockModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StockJpaRepository extends JpaRepository<StockModel, Long> {
    Optional<StockModel> findByProductId(Long productId);
}
