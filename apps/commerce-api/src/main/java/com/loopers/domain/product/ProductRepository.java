package com.loopers.domain.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.List;

public interface ProductRepository {
    ProductModel save(ProductModel product);
    Optional<ProductModel> find(Long id);
    Optional<ProductModel> findActive(Long id);

    /**
     * 고객 목록: active 상품만, 브랜드 필터(옵셔널), 정렬, 페이징.
     * brandId 가 null 이면 전체 active 상품을 반환한다.
     */
    List<ProductModel> findAllActive(Long brandId, ProductSortType sort, int page, int size);

    /**
     * 삭제 포함 전체 목록: 브랜드 필터(옵셔널), 페이징.
     * brandId 가 null 이면 전체 상품을 반환한다.
     */
    Page<ProductModel> findAll(Long brandId, Pageable pageable);

    List<ProductModel> findAllActiveByBrandId(Long brandId);

    /**
     * active 상품의 like_count 를 1 증가시킨다.
     * @return 영향 행 수 (0 또는 1)
     */
    int incrementLikeCount(Long id);

    /**
     * active 상품의 like_count 를 1 감소시킨다.
     * @return 영향 행 수 (0 또는 1)
     */
    int decrementLikeCount(Long id);
}
