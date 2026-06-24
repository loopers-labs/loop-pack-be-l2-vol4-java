package com.loopers.application.product;

import com.loopers.domain.product.ProductListEntry;

/**
 * 상품 목록 항목의 <b>사용자 무관</b> 캐시 표현 (Redis 직렬화 대상). {@link ProductListItemInfo}에서 사용자별
 * 좋아요 여부(liked)만 뺀 것 — 페이지 단위로 캐시 1벌을 공유하고, liked는 Facade가 batch 조회 후 덧붙인다.
 */
public record CachedProductListItem(
    Long id,
    Long brandId,
    String brandName,
    String name,
    String description,
    String imageUrl,
    Long price,
    Integer stock,
    Long likesCount
) {
    public static CachedProductListItem from(ProductListEntry entry) {
        return new CachedProductListItem(
            entry.product().getId(),
            entry.product().getBrandId(),
            entry.brandName(),
            entry.product().getName(),
            entry.product().getDescription(),
            entry.product().getImageUrl(),
            entry.product().getPrice(),
            entry.stock(),
            entry.product().getLikesCount()
        );
    }

    public ProductListItemInfo toInfo(boolean liked) {
        return new ProductListItemInfo(id, brandId, brandName, name, description, imageUrl, price, stock, likesCount, liked);
    }
}
