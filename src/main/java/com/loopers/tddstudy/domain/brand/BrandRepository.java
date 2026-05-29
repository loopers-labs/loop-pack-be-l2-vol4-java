package com.loopers.tddstudy.domain.brand;

import java.util.List;
import java.util.Optional;


public interface BrandRepository {

    Brand save(Brand brand);

    Optional<Brand> findById(Long id);

    List<Brand> findAll();
}
