package com.loopers.domain.product;

import java.util.Collection;
import java.util.Optional;
import java.util.List;

public interface ProductRepository {
    ProductModel save(ProductModel product);
    Optional<ProductModel> find(Long id);

    /** 특정 브랜드의 활성 상품 전체 — Brand 비활성 시 cascade 전파용 (01 §7.5). */
    List<ProductModel> findActiveByBrandId(Long brandId);

    /**
     * 활성 상품 목록 — 브랜드 필터(null=전체) + 정렬 + 키셋(커서) 페이지 (UC-03).
     * cursor가 null이면 첫 페이지. hasNext 판별을 위해 호출부가 요청한 size보다 1건 많은(최대 size+1)
     * 결과를 반환한다(잘라내기·nextCursor 생성은 상위에서 처리).
     */
    List<ProductModel> findActivePage(Long brandId, ProductSortType sort, ProductCursor cursor, int size);

    /** 주어진 id 들 중 활성 상품만 batch 조회 — 좋아요한 상품 목록 조합 N+1 회피 (UC-07). */
    List<ProductModel> findActiveByIds(Collection<Long> ids);

    /** 좋아요 수 원자적 +1 (동시 좋아요의 lost update 차단 — 04 §4.2). */
    void incrementLikesCount(Long id);

    /** 좋아요 수 원자적 -1 (음수 가드 포함). */
    void decrementLikesCount(Long id);
}
