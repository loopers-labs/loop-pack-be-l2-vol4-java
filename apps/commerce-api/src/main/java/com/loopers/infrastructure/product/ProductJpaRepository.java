package com.loopers.infrastructure.product;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductJpaRepository extends JpaRepository<ProductEntity, Long> {
    List<ProductEntity> findByBrandIdAndDeletedAtIsNull(Long brandId);

    List<ProductEntity> findByDeletedAtIsNull(Pageable pageable);

    List<ProductEntity> findByBrandIdAndDeletedAtIsNull(Long brandId, Pageable pageable);
}
