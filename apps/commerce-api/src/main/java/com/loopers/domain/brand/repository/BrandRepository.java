package com.loopers.domain.brand.repository;

import com.loopers.domain.brand.model.Brand;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface BrandRepository {
    Optional<Brand> findById(Long id);
    Page<Brand> findAll(Pageable pageable);
    Brand save(Brand brand);
    boolean existsByName(String name);
}
