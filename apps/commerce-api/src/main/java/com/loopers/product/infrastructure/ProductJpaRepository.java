package com.loopers.product.infrastructure;

import com.loopers.product.domain.ProductModel;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductJpaRepository extends JpaRepository<ProductModel, Long> {
}
