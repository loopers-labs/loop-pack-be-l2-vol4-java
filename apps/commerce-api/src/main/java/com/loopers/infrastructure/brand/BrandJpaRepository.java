package com.loopers.infrastructure.brand;

import org.springframework.data.jpa.repository.JpaRepository;

import com.loopers.domain.brand.BrandModel;

public interface BrandJpaRepository extends JpaRepository<BrandModel, Long> {

    boolean existsByNameValueAndDeletedAtIsNull(String value);
}
