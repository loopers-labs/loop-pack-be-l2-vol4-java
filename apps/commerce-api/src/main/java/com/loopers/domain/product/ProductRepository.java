package com.loopers.domain.product;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    ProductModel save(ProductModel product);

    Optional<ProductModel> findById(Long id);

    /** 재고 차감용 조회 — 비관적 쓰기 락으로 동시 차감을 직렬화한다. */
    Optional<ProductModel> findByIdForUpdate(Long id);

    /** 좋아요 수 원자적 증가 (동시 좋아요의 lost update 방지) */
    void increaseLikeCount(Long id);

    /** 좋아요 수 원자적 감소 */
    void decreaseLikeCount(Long id);

    /**
     * 상품 목록 조회. brandId 가 null 이면 전체, 아니면 해당 브랜드 상품만.
     */
    List<ProductModel> findAll(Long brandId, ProductSortOption sort, int page, int size);
}
