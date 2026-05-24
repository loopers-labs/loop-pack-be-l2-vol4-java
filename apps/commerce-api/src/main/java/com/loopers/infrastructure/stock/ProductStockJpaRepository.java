package com.loopers.infrastructure.stock;

import com.loopers.domain.stock.ProductStock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductStockJpaRepository extends JpaRepository<ProductStock, Long> {

    Optional<ProductStock> findByProductId(Long productId);

    List<ProductStock> findAllByProductIdIn(Collection<Long> productIds);
}
