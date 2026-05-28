package com.loopers.infrastructure.product;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductJpaRepository extends JpaRepository<ProductJpaEntity, Long> {
    Optional<ProductJpaEntity> findByIdAndDeletedAtIsNull(Long id);

    List<ProductJpaEntity> findAllByIdInAndDeletedAtIsNull(List<Long> ids);

    List<ProductJpaEntity> findAllByDeletedAtIsNull(Pageable pageable);

    List<ProductJpaEntity> findAllByBrandIdAndDeletedAtIsNull(Long brandId);

    List<ProductJpaEntity> findAllByBrandIdAndDeletedAtIsNull(Long brandId, Pageable pageable);
}
