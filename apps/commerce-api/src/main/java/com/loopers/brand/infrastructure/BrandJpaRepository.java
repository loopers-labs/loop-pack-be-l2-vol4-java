package com.loopers.brand.infrastructure;

import com.loopers.brand.domain.BrandModel;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BrandJpaRepository extends JpaRepository<BrandModel, Long> {
}
