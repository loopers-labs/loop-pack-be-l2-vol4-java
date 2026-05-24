package com.loopers.domain.brand;

import com.loopers.support.pagination.PageQuery;
import com.loopers.support.pagination.PageResult;

import java.util.Optional;

public interface BrandRepository {
    Brand save(Brand brand);
    Optional<Brand> findActiveById(Long brandId);
    PageResult<Brand> findActiveAll(PageQuery query);
}
