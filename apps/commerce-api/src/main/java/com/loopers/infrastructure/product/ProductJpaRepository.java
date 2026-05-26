package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductJpaRepository extends JpaRepository<ProductModel, Long> {

    Optional<ProductModel> findByIdAndDeletedAtIsNull(Long id);
}
