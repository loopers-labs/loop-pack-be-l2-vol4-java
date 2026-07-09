package com.loopers.domain.product;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 상품 리스팅 read model(product_metrics) 조회 포트. 리스팅의 정렬/필터/페이지는 이 read model 단일 테이블에서
 * 끝내고(좋아요순 포함), 표시 필드(name/description/image)는 product 를 id batch 로 조합한다(week7 CQRS).
 */
public interface ProductMetricsRepository {

    /**
     * 활성 상품 id 페이지 — brand 필터(null=전체) + 정렬 + 페이지. <b>정렬 순서를 보존</b>해 반환한다.
     * 정렬키(like_count/price/product_id)가 모두 product_metrics 에 있어 단일 테이블 인덱스 스캔으로 처리된다.
     */
    List<Long> findActiveIdsPage(Long brandId, ProductSortType sort, int page, int size);

    /** 좋아요 수 batch 조회(목록/내가 좋아요한 목록 조합). 행 없는 id 는 결과에서 빠진다 → 호출측 getOrDefault(id,0). */
    Map<Long, Long> findLikeCounts(Collection<Long> productIds);

    /** 좋아요 수 단건(상세/단건 응답). 행 없으면 0. */
    long getLikeCount(Long productId);
}
