package com.loopers.domain.product;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {

    ProductModel save(ProductModel product);

    ProductModel getActiveById(Long id);

    Optional<ProductModel> findActiveById(Long id);

    List<ProductModel> findActiveByBrandId(Long brandId);
}
