package com.loopers.application.brand;

import com.loopers.domain.brand.BrandModel;

import java.util.Optional;

public interface BrandRepository {
    BrandModel save(BrandModel brand);
    Optional<BrandModel> findById(Long id);
    java.util.List<BrandModel> findByIds(java.util.List<Long> ids);
    java.util.List<BrandModel> findAll();
}
