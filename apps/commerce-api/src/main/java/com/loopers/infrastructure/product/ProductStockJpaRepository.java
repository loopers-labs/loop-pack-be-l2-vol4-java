package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductStockModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductStockJpaRepository extends JpaRepository<ProductStockModel, Long> {

    Optional<ProductStockModel> findByProductIdAndDeletedAtIsNull(Long productId);
}