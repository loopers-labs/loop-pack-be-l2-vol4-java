package com.loopers.domain.catalog.brand;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BrandRepository {
    Brand save(Brand brand);

    Optional<Brand> find(Long id);

    Optional<Brand> findActive(Long id);

    List<Brand> findAllByIds(Collection<Long> ids);

    List<Brand> findAll(int page, int size);

    long countAll();
}
