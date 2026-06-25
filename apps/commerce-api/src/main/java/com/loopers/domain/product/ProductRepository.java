package com.loopers.domain.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    ProductEntity save(ProductEntity product);
    Optional<ProductEntity> find(String id);
    Page<ProductEntity> findAll(String brandId, Pageable pageable);
    List<String> findIdsByBrandId(String brandId);
    List<ProductEntity> findAllByIds(List<String> ids);
    void incrementLikeCount(String id);
    void decrementLikeCount(String id);
}
