package com.loopers.domain.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    ProductModel save(ProductModel product);

    Optional<ProductModel> find(Long id);

    List<ProductModel> findAllByIds(List<Long> ids);

    List<ProductModel> findAllByBrandId(Long brandId);

    Page<ProductModel> findAllByBrandId(Long brandId, Pageable pageable);

    Page<ProductModel> findProducts(Long brandId, Pageable pageable);

    void softDeleteAllByBrandId(Long brandId, ZonedDateTime deletedAt);
}
