package com.loopers.infrastructure.brand;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BrandJpaRepository extends JpaRepository<BrandJpaEntity, Long> {
    Optional<BrandJpaEntity> findByNameAndDeletedAtIsNull(String name);
    Page<BrandJpaEntity> findAllByDeletedAtIsNull(Pageable pageable);
    List<BrandJpaEntity> findAllByIdInAndDeletedAtIsNull(List<Long> ids);
}
