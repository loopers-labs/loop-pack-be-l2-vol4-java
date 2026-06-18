package com.loopers.application.product;

/**
 * 상품 캐시 무효화 이벤트.
 *
 * <p>상품/재고 변경 트랜잭션 안에서 발행되고, {@link ProductCacheEvictListener}가
 * <strong>커밋 이후</strong>에 실제 캐시 삭제를 수행한다. "커밋 후 삭제"로 무효화-쓰기 race
 * (커밋 전 evict → 다른 요청이 옛 값 재캐싱)를 방지한다.
 *
 * @param productId  상세 캐시를 비울 상품 id. null 이면 상세는 건드리지 않음(예: 신규 등록).
 * @param evictList  목록 캐시 전체를 비울지 여부(상품 추가/삭제/가격변경 시 true).
 */
public record ProductCacheEvictEvent(Long productId, boolean evictList) {

    public static ProductCacheEvictEvent detailAndList(Long productId) {
        return new ProductCacheEvictEvent(productId, true);
    }

    public static ProductCacheEvictEvent listOnly() {
        return new ProductCacheEvictEvent(null, true);
    }

    public static ProductCacheEvictEvent detailOnly(Long productId) {
        return new ProductCacheEvictEvent(productId, false);
    }
}
