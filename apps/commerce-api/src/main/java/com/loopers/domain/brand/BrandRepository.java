package com.loopers.domain.brand;

import java.util.List;
import java.util.Optional;

public interface BrandRepository {
    Brand save(Brand brand);
    Optional<Brand> find(Long id);
    List<Brand> findAllByIds(List<Long> ids);
    List<Brand> findAll(int page, int size);
}
