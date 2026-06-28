package com.loopers.brand.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BrandRepository {
    Brand save(Brand brand);

    Optional<Brand> find(Long id);

    List<Brand> findAll();

    List<Brand> findAllByIds(Collection<Long> ids);

    boolean existsById(Long id);
}
