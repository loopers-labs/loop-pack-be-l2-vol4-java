package com.loopers.brand.domain;

import java.util.Optional;

public interface BrandRepository {
    BrandModel save(BrandModel brand);
    Optional<BrandModel> find(Long id);
}
