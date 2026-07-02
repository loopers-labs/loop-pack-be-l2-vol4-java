package com.loopers.product.infrastructure;

import com.loopers.product.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductJpaRepository extends JpaRepository<Product, Long> {
    List<Product> findByBrandId(Long brandId);
}
