package com.loopers.application.product;

import java.util.Optional;

public interface ProductListCacheRepository {
    Optional<ProductListCache> find(Long brandId, String sort, int page, int size);
    void save(Long brandId, String sort, int page, int size, ProductListCache cache);
}
