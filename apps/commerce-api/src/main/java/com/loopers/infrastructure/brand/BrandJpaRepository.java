package com.loopers.infrastructure.brand;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BrandJpaRepository extends JpaRepository<BrandJpaEntity, Long> {
    Optional<BrandJpaEntity> findByIdAndDeletedAtIsNull(Long id);

    List<BrandJpaEntity> findAllByIdInAndDeletedAtIsNull(List<Long> ids);

    List<BrandJpaEntity> findAllByDeletedAtIsNull(Pageable pageable);
}
