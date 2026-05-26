package com.loopers.domain.brand;

import java.util.List;
import java.util.Optional;

public interface BrandRepository {
    Brand save(Brand brand);

    Optional<Brand> find(Long id);

    List<Brand> findAll(int page, int size);

    boolean existsById(Long id);

    void delete(Long id);
}
