package com.loopers.domain.product;

import java.util.Optional;
import java.util.List;

public interface ProductRepository {
    ProductModel save(ProductModel product);
    Optional<ProductModel> find(Long id);
    int deductStock(Long id, int quantity);
    List<ProductModel> findAll();
    ProductPage search(ProductSearchCondition condition);
    void delete(Long id);
}
