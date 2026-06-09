package com.loopers.product.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    ProductModel save(ProductModel product);

    Optional<ProductModel> find(Long id);

    List<ProductModel> findAll();

    List<ProductModel> findByBrandId(Long brandId);

    List<ProductModel> findAllByIds(Collection<Long> ids);
}
