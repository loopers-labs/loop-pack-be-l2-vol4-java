package com.loopers.infrastructure.product;

import com.loopers.domain.product.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductJpaRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByIdAndDeletedAtIsNull(Long productId);

    Page<Product> findByDeletedAtIsNull(Pageable pageable);

    Page<Product> findByBrandIdAndDeletedAtIsNull(Long brandId, Pageable pageable);
}
