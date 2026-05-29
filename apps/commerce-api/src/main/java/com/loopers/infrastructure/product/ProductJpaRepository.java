package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductJpaRepository extends JpaRepository<ProductModel, Long> {
    Page<ProductModel> findAllByBrandId(Long brandId, Pageable pageable);
    List<ProductModel> findAllByBrandId(Long brandId);
}
