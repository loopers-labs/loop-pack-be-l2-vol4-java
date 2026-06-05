package com.loopers.brand.domain;

import java.util.List;
import java.util.Optional;

public interface BrandRepository {
    Brand save(Brand brand);
    Optional<Brand> findById(Long id);
    List<Brand> findAll();
    List<Brand> findAllByIdIn(List<Long> ids);
    boolean existsById(Long id);
    boolean existsByName(String name);
    int softDeleteById(Long id);
}
