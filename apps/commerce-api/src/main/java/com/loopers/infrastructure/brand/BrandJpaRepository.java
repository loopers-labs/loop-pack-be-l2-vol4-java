package com.loopers.infrastructure.brand;

import com.loopers.domain.brand.BrandModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BrandJpaRepository extends JpaRepository<BrandModel, UUID> {
    Optional<BrandModel> findByIdAndDeletedAtIsNull(UUID id);
}
