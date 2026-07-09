package com.loopers.tddstudy.domain.product;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {

    Product save(Product product);

    Optional<Product> findById(Long id);

    Optional<Product> findByIdWithLock(Long id);

    List<Product> findAll();

    List<Product> findAllByBrandId(Long brandId);

    List<Product> findAllByFilter(Long brandId, String sort);


    List<Product> saveAll(List<Product> products);

    List<Product> findTop10ByOrderByLikeCountDesc();
}
