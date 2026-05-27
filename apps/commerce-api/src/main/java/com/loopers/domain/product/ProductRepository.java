package com.loopers.domain.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    ProductModel save(ProductModel product);
    Optional<ProductModel> findById(Long id);               // 삭제 여부 무관 (소유권 검증 등)
    Optional<ProductModel> findActiveById(Long id);         // 활성 상품만
    Page<ProductModel> findAllActive(Pageable pageable, ProductSearchCondition condition);
    List<ProductModel> findAllActiveByIds(List<Long> ids);
    List<ProductModel> findAllByBrandId(Long brandId);
    void incrementLikeCount(Long productId);
    void decrementLikeCount(Long productId);
}
