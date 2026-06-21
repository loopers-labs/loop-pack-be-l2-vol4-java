package com.loopers.product.domain;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    ProductModel save(ProductModel product);
    Optional<ProductModel> find(Long id);
    List<ProductSummaryModel> findAll(SortCondition sort, Long brandId, boolean inStock, int page, int size);
    List<ProductModel> findAllByIds(List<Long> ids);
    List<ProductModel> findAllByBrandId(Long brandId);
    // [fix] 좋아요 등록·취소 시 likeCount 미연동 → 원자 UPDATE 메서드 추가
    void incrementLikeCount(Long productId);
    void decrementLikeCount(Long productId);
}
