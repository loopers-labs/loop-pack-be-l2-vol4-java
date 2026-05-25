package com.loopers.infrastructure.brand;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BrandJpaRepository extends JpaRepository<BrandEntity, Long> {
    Optional<BrandEntity> findByIdAndDeletedAtIsNull(Long id);
    Page<BrandEntity> findAllByDeletedAtIsNull(Pageable pageable);
}
