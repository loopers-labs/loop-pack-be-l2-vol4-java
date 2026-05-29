package com.loopers.domain.product;

import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    ProductModel save(ProductModel product);
    Optional<ProductModel> find(Long id);
    Optional<ProductModel> findWithLock(Long id);
    List<ProductModel> findAll(Long brandId, Pageable pageable);
    void delete(Long id);
    void deleteByBrandId(Long brandId);
    List<ProductModel> findAllOrderByLikeCountDesc(Long brandId, Pageable pageable);
}
