package com.loopers.domain.brand;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BrandRepository {
    BrandModel save(BrandModel brand);
    Optional<BrandModel> find(Long id);
    Optional<BrandModel> findActive(Long id);
    List<BrandModel> findAllByIdIn(Collection<Long> ids);
    boolean existsByName(String name);
}
