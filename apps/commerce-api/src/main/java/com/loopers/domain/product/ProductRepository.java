package com.loopers.domain.product;

import com.loopers.domain.product.enums.ProductSortType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    ProductModel save(ProductModel product);
    Optional<ProductModel> find(Long id);
    boolean existsByBrandIdAndName(Long brandId, String name);
    List<ProductModel> findAllByBrandId(Long brandId);
    void suspendAllByBrandId(Long brandId);
    Page<ProductModel> findAll(Long brandId, ProductSortType sort, Pageable pageable);
    Page<ProductModel> findAllForAdmin(Long brandId, Pageable pageable);
    List<ProductModel> findAllByIds(List<Long> ids);
    void increaseLikeCount(Long productId);
    void decreaseLikeCount(Long productId);
}
