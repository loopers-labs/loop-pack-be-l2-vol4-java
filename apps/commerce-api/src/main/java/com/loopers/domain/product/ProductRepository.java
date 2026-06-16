package com.loopers.domain.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    ProductEntity save(ProductEntity product);
    Optional<ProductEntity> find(Long id);
    Page<ProductEntity> findAll(Long brandId, Pageable pageable);
    List<Long> findIdsByBrandId(Long brandId);
    List<ProductEntity> findAllByIds(List<Long> ids);
    void incrementLikeCount(Long id);
    void decrementLikeCount(Long id);
}
