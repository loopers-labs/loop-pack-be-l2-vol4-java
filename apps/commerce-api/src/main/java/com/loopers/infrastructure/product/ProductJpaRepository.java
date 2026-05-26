package com.loopers.infrastructure.product;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.loopers.domain.product.ProductModel;

public interface ProductJpaRepository extends JpaRepository<ProductModel, Long> {

    Optional<ProductModel> findByIdAndDeletedAtIsNull(Long id);

    List<ProductModel> findByBrandIdAndDeletedAtIsNull(Long brandId);
}
