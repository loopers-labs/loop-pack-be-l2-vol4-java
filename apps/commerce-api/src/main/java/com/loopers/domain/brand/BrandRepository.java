package com.loopers.domain.brand;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BrandRepository {
    Optional<BrandModel> findById(Long id);
    List<BrandModel> findAllByIdIn(Collection<Long> ids);
    BrandModel save(BrandModel brand);
}
