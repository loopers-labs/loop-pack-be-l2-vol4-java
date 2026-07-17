package com.loopers.product.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    Product save(Product product);

    Optional<Product> find(Long id);

    List<Product> findAll();

    List<Product> findByBrandId(Long brandId);

    List<Product> findAllByIds(Collection<Long> ids);
}
