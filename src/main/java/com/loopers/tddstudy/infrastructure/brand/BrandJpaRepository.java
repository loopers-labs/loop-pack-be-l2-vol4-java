package com.loopers.tddstudy.infrastructure.brand;

import com.loopers.tddstudy.domain.brand.Brand;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BrandJpaRepository extends JpaRepository<Brand, Long> {
}
