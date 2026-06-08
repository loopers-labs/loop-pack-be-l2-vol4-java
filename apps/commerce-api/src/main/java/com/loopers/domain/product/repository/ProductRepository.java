package com.loopers.domain.product.repository;

import com.loopers.domain.product.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    Optional<Product> findById(Long id);
    List<Product> findAllByIdIn(List<Long> ids);
    Page<Product> findAll(Long brandId, Pageable pageable);
    List<Long> findIdsByBrandId(Long brandId);
    int softDeleteAllByBrandId(Long brandId);
    Product save(Product product);
    int incrementLikeCount(Long productId);
    int decrementLikeCount(Long productId);
}
