package com.loopers.infrastructure.product;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductStockJpaRepository extends JpaRepository<ProductStockEntity, Long> {
    Optional<ProductStockEntity> findByProductId(Long productId);
}
