package com.loopers.brand.infrastructure;

import com.loopers.brand.domain.BrandModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BrandJpaRepository extends JpaRepository<BrandModel, Long> {
    Optional<BrandModel> findByIdAndDeletedAtIsNull(Long id);
}
