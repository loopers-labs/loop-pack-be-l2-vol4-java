package com.loopers.product.infrastructure;

import com.loopers.product.domain.ProductModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductJpaRepository extends JpaRepository<ProductModel, Long> {
    List<ProductModel> findByBrandId(Long brandId);
}
