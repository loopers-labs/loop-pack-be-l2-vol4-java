package com.loopers.application.product;



import com.loopers.domain.product.ProductModel;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface ProductRepository {
    ProductModel save(ProductModel product);
    Optional<ProductModel> findById(Long id);
    List<ProductModel> findByIds(List<Long> ids);
    List<ProductModel> findAll();
    Page<ProductModel> findAll(Long brandId, String sort, Pageable pageable);
    void delete(Long id);
    void deleteByBrandId(Long brandId);
    Optional<ProductModel> findByIdWithLock(Long id);
}
