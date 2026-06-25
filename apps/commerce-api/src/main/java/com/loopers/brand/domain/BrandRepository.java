package com.loopers.brand.domain;

import com.loopers.shared.pagination.PageQuery;
import com.loopers.shared.pagination.PageResult;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BrandRepository {
    Brand save(Brand brand);
    Optional<Brand> findActiveById(Long brandId);
    List<Brand> findActiveAllByIds(Collection<Long> brandIds);
    PageResult<Brand> findActiveAll(PageQuery query);
}
