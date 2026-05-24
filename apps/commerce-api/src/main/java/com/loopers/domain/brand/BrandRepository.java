package com.loopers.domain.brand;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface BrandRepository {
    Brand save(Brand brand);
    Optional<Brand> findActiveById(Long brandId);
    Page<Brand> findActiveAll(Pageable pageable);
}
