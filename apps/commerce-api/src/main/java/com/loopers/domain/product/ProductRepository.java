package com.loopers.domain.product;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    ProductModel save(ProductModel product);
    Optional<ProductModel> find(Long id);
    boolean existsById(Long id);

    /**
     * 정렬 조건과 (옵션) 브랜드 필터로 상품 목록을 반환한다.
     * @param brandId null 이면 전체 브랜드
     */
    List<ProductModel> findAll(ProductSortType sortType, Long brandId);

    /**
     * 좋아요 카운트 원자적 증가 (denormalized like_count). lost update 방지.
     */
    void incrementLikeCount(Long productId);

    /**
     * 좋아요 카운트 원자적 감소. 0 미만으로 떨어지지 않도록 인프라가 보장한다.
     */
    void decrementLikeCount(Long productId);
}
