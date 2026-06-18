package com.loopers.application.product;

import com.loopers.infrastructure.product.ProductCacheStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 상품 캐시 무효화 리스너.
 *
 * <p>{@link TransactionPhase#AFTER_COMMIT} 으로 동작 — 변경 트랜잭션이 성공적으로 커밋된 뒤에만
 * 캐시를 삭제한다. 롤백 시에는 이벤트가 소비되지 않아 캐시도 건드리지 않는다.
 * 무효화는 "삭제만"(새 값 쓰기 없음) 하여, 다음 조회가 최신 DB 값으로 캐시를 다시 채우게 한다.
 */
@RequiredArgsConstructor
@Component
public class ProductCacheEvictListener {

    private final ProductCacheStore productCacheStore;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProductCacheEvict(ProductCacheEvictEvent event) {
        if (event.productId() != null) {
            productCacheStore.evictDetail(event.productId());
        }
        if (event.evictList()) {
            productCacheStore.evictAllList();
        }
    }
}
