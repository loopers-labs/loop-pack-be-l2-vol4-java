package com.loopers.domain.brand.repository;

import com.loopers.domain.brand.model.Brand;
import java.util.Optional;

public interface BrandRepository {
    Optional<Brand> findById(Long id);
    Brand save(Brand brand);
}
