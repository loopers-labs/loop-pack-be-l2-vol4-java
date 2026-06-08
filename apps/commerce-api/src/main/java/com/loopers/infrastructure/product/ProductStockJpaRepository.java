package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductStockModel;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductStockJpaRepository extends JpaRepository<ProductStockModel, Long> {
}
