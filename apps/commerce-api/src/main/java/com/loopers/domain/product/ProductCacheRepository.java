package com.loopers.domain.product;

import org.springframework.data.domain.Page;

import java.util.Optional;

public interface ProductCacheRepository {

    Optional<Product> findById(Long productId);

    void save(Product product);

    Optional<Page<Product>> findAll(Long brandId, ProductSort sort, int page, int size);

    void saveAll(Long brandId, ProductSort sort, int page, int size, Page<Product> products);

    void evict(Long productId);

    void evictAll();
}