package com.loopers.tddstudy.domain.product;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {

    Product save(Product product);

    Optional<Product> findById(Long id);

    Optional<Product> findByIdWithLock(Long id);

    List<Product> findAll();

    List<Product> findAllByBrandId(Long brandId);
}
