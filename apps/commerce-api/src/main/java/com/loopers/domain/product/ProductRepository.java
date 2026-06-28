package com.loopers.domain.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    ProductModel save(ProductModel product);
    List<ProductModel> saveAll(List<ProductModel> products);
    Optional<ProductModel> findById(Long id);
    Page<ProductModel> findAll(Long brandId, ProductSortType sort, Pageable pageable);
    List<ProductModel> findAllByIdsWithLock(List<Long> ids);
    List<Long> findIdsByBrandId(Long brandId);
    void delete(Long id);
    void bulkSoftDelete(Long brandId);
    boolean existsById(Long id);
}
