package com.loopers.domain.brand;

import com.loopers.domain.common.PageResult;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BrandRepository {

    Optional<Brand> find(Long id);
    List<Brand> findAll();
    PageResult<Brand> findAll(int page, int size);
    List<Brand> findAllByIds(Collection<Long> ids);
    Brand save(Brand brand);
    void update(Brand brand);

}

