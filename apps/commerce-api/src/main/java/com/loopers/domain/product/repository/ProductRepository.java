package com.loopers.domain.product.repository;

import com.loopers.domain.product.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    Optional<Product> findById(Long id);
    Page<Product> findAll(Long brandId, Pageable pageable);
    List<Long> findIdsByBrandId(Long brandId);
    int softDeleteAllByBrandId(Long brandId);
    Product save(Product product);
}
