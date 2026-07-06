package com.loopers.application.like;

import com.loopers.config.CacheConfig;
import com.loopers.domain.like.ProductLikedEvent;
import com.loopers.domain.like.ProductUnlikedEvent;
import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 좋아요 사실(핵심)이 커밋된 뒤에 likeCount 집계(부가)를 반영한다.
 * AFTER_COMMIT 이라 집계가 실패해도 좋아요 자체는 이미 커밋되어 안전하다 — "집계 실패와 무관하게 좋아요는 성공".
 * 단, 스프링 이벤트는 재시도가 없어 실패 시 카운트가 어긋날 수 있다(likeCount 는 불변식이 아니므로 감수하거나 재집계로 보정).
 */
@RequiredArgsConstructor
@Component
public class ProductLikeEventHandler {

    private final ProductService productService;
    private final CacheManager cacheManager;

    // @Async 가 없으면 요청 스레드가 원본 커넥션을 반납하기 전에 집계용 커넥션을 추가로 빌려
    // (연산당 2개 점유) 동시 요청 시 풀이 고갈된다. 별도 스레드로 넘겨 원본 커넥션을 먼저 반납하게 한다.
    // 집계의 트랜잭션은 productService 의 @Transactional 이 연다 (비동기 스레드에는 기존 트랜잭션이 없다).
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProductLiked(ProductLikedEvent event) {
        productService.increaseLikeCount(event.productId());
        evictProductDetail(event.productId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProductUnliked(ProductUnlikedEvent event) {
        productService.decreaseLikeCount(event.productId());
        evictProductDetail(event.productId());
    }

    // 집계 반영(커밋) 후의 재무효화. LikeFacade 의 즉시 무효화와 집계 반영 사이에 조회가 끼면
    // 갱신 전 값이 다시 캐시에 실리므로(stale re-cache), 반영 이후 시점의 무효화로 레이스를 닫는다.
    private void evictProductDetail(Long productId) {
        Cache cache = cacheManager.getCache(CacheConfig.PRODUCT_DETAIL);
        if (cache != null) {
            cache.evict(productId);
        }
    }
}
