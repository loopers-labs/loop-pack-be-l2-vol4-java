package com.loopers.brand.infrastructure;

import com.loopers.brand.domain.Brand;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BrandJpaRepository extends JpaRepository<Brand, Long> {
}
