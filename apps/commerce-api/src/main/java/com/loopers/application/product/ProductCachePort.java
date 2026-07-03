package com.loopers.application.product;

import com.loopers.domain.product.ProductSortType;

import java.util.List;
import java.util.Optional;

/**
 * 상품 조회 캐시 포트 — Look-aside 패턴.
 * <p>
 * 키 전략 / TTL / 무효화는 어댑터(인프라) 구현에 위임한다. 애플리케이션은
 * 의도 ("상세 가져오기", "목록 가져오기", "이 상품 캐시 비우기") 만 표현한다.
 * <p>
 * 모든 메서드는 캐시 실패(Redis down 등) 시 예외 없이 안전하게 동작해야 한다:
 * - get: Optional.empty() 반환 (호출자는 DB fallback)
 * - put / evict: 조용히 통과 (서비스 동작에 영향 X)
 */
public interface ProductCachePort {

    Optional<ProductDetailInfo> getDetail(Long productId);

    void putDetail(ProductDetailInfo info);

    void evictDetail(Long productId);

    Optional<List<ProductInfo>> getList(Long brandId, ProductSortType sortType);

    void putList(Long brandId, ProductSortType sortType, List<ProductInfo> infos);

    /**
     * 특정 브랜드의 모든 목록 캐시 무효화 (정렬 조합 전체).
     * 상품 생성/수정 시 호출. brandId 가 null 이면 "전체" 스코프 목록 캐시를 무효화한다.
     */
    void evictListsByBrand(Long brandId);
}
