package com.loopers.domain.brand;

import java.util.List;
import java.util.Optional;

public interface BrandRepository {
    BrandModel save(BrandModel brand);
    Optional<BrandModel> find(Long id);
    List<BrandModel> findAllByIds(List<Long> ids);
    List<BrandModel> findAll();
    List<BrandModel> findAll(int page, int size);
}
