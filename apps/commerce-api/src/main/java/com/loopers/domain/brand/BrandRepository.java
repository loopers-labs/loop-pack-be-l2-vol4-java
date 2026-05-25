package com.loopers.domain.brand;

import java.util.Optional;

public interface BrandRepository {
    BrandModel save(BrandModel brand);
    Optional<BrandModel> find(Long id);
    Optional<BrandModel> findActive(Long id);
    boolean existsByName(String name);
}
