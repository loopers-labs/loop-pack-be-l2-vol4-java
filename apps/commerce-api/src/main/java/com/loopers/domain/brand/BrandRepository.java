package com.loopers.domain.brand;

import java.util.Optional;

public interface BrandRepository {
    Optional<BrandModel> findById(Long id);
    BrandModel save(BrandModel brand);
}
