package com.loopers.application.product;

import com.loopers.domain.productrank.RankedProduct;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * 상품 캐시 포트. application 이 소유하고 infrastructure 어댑터가 구현한다(ports & adapters).
 * Facade 는 Redis 를 모르고 이 포트만 의존한다(seam) — 캐시 교체·제거가 Facade 에 안 번진다.
 */
public interface ProductCache {

    Optional<ProductDetailInfo> getDetail(Long id);

    void putDetail(ProductDetailInfo info, Duration ttl);

    void evictDetail(Long id);

    /** (brand·likes_desc) 첫 페이지용 top-N 정렬 블롭(id+rank like_count). 순수 TTL — 좋아요엔 evict 안 함. */
    Optional<List<RankedProduct>> getLikesBlob(Long brandId);

    void putLikesBlob(Long brandId, List<RankedProduct> ranked, Duration ttl);
}
