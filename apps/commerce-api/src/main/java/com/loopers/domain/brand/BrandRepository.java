package com.loopers.domain.brand;

import java.util.List;
import java.util.Optional;

public interface BrandRepository {
    Brand save(Brand brand);
    Optional<Brand> findById(Long id);
    Optional<Brand> findByName(String name);
    List<Brand> findAll();
    boolean existsByName(String name);
}
