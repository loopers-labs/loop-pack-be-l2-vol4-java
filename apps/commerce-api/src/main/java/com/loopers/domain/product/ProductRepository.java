package com.loopers.domain.product;

import java.util.Optional;
import java.util.List;

public interface ProductRepository {
    ProductModel save(ProductModel product);
    Optional<ProductModel> findById(Long id);
    List<ProductModel> findByIds(List<Long> ids);
    List<ProductModel> findAll();
    org.springframework.data.domain.Page<ProductModel> findAll(Long brandId, String sort, org.springframework.data.domain.Pageable pageable);
    void delete(Long id);
    void deleteByBrandId(Long brandId);
    Optional<ProductModel> findByIdWithLock(Long id);
}
