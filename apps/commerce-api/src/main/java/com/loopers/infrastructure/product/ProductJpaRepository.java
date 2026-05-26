package com.loopers.infrastructure.product;

import org.springframework.data.jpa.repository.JpaRepository;

import com.loopers.domain.product.ProductModel;

public interface ProductJpaRepository extends JpaRepository<ProductModel, Long> {
}
