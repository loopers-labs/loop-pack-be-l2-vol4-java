package com.loopers.application.product;

import com.loopers.infrastructure.product.ProductCacheStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@RequiredArgsConstructor
@Component
public class ProductCacheEvictListener {

    private final ProductCacheStore productCacheStore;

    // AFTER_COMMIT: 커밋 전에 지우면 그 사이 읽기 요청이 옛 값을 다시 캐시에 채울 수 있다.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ProductCacheEvictEvent event) {
        try {
            productCacheStore.evictAll(event.productIds());
        } catch (Exception e) {
            log.warn("상품 캐시 무효화 실패. productIds={}", event.productIds(), e);
        }
    }
}
