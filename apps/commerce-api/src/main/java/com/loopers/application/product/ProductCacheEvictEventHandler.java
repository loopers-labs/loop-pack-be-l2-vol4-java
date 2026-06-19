package com.loopers.application.product;

import com.loopers.config.cache.CacheConfig;
import com.loopers.domain.like.event.ProductLikedEvent;
import com.loopers.domain.like.event.ProductUnlikedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Optional;
import java.util.Set;

/**
 * 좋아요 이벤트에 따른 캐시 무효화.
 *
 *  - productDetail::{productId} : 좋아요 수가 응답에 포함되므로 evict
 *  - productList::*              : 좋아요 정렬이 영향 받으므로 와일드카드 evict
 *
 * AFTER_COMMIT 으로 like 트랜잭션 응답엔 영향 없음.
 * KEYS 명령은 학습용으로 사용 — 운영 환경에선 SCAN 으로 교체 권장.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class ProductCacheEvictEventHandler {

    private final CacheManager cacheManager;
    private final RedisTemplate<String, Object> objectRedisTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLiked(ProductLikedEvent event) {
        evictProductDetail(event.productId());
        evictAllProductLists();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUnliked(ProductUnlikedEvent event) {
        evictProductDetail(event.productId());
        evictAllProductLists();
    }

    private void evictProductDetail(Long productId) {
        try {
            Optional.ofNullable(cacheManager.getCache(CacheConfig.CACHE_PRODUCT_DETAIL))
                .ifPresent((Cache cache) -> cache.evict(productId));
        } catch (Exception e) {
            log.warn("[Cache EVICT FAIL] productDetail id={}: {}", productId, e.getMessage());
        }
    }

    private void evictAllProductLists() {
        try {
            Set<String> keys = objectRedisTemplate.keys(CacheConfig.CACHE_PRODUCT_LIST_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                objectRedisTemplate.delete(keys);
                log.debug("[Cache EVICT] productList keys={}", keys.size());
            }
        } catch (Exception e) {
            log.warn("[Cache EVICT FAIL] productList: {}", e.getMessage());
        }
    }
}
