package com.loopers.domain.brand;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface BrandRepository {
    BrandEntity save(BrandEntity brand);
    Optional<BrandEntity> findById(Long id);
    Page<BrandEntity> findAll(Pageable pageable);
    Optional<BrandEntity> findByName(String name);
}
