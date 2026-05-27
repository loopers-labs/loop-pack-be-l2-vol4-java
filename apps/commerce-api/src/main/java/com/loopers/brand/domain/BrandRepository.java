package com.loopers.brand.domain;

import java.util.List;
import java.util.Optional;

public interface BrandRepository {
    BrandModel save(BrandModel brand);
    Optional<BrandModel> find(Long id);
    List<BrandModel> findAllByIds(List<Long> ids);
}
