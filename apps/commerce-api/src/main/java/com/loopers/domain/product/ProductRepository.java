package com.loopers.domain.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface ProductRepository {
    Product save(Product product);
    Optional<Product> find(Long id);
    Page<Product> findAll(Long brandId, Pageable pageable);
    void deleteAllByBrandId(Long brandId);
    void incrementLikeCount(Long id);
    void decrementLikeCount(Long id);
    void adjustLikeCount(Long id, long amount);
}
