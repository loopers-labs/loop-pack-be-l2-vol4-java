package com.loopers.infrastructure.brand;

import com.loopers.domain.brand.Brand;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BrandJpaRepository extends JpaRepository<Brand, Long> {
    Optional<Brand> findByIdAndDeletedAtIsNull(Long id);
    Optional<Brand> findByNameAndDeletedAtIsNull(String name);
    List<Brand> findAllByDeletedAtIsNull();
    boolean existsByNameAndDeletedAtIsNull(String name);
}
