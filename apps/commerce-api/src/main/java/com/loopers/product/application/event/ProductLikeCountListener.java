package com.loopers.product.application.event;

import com.loopers.product.domain.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 좋아요 상태 전이 이벤트를 받아 products.like_count 를 원자적으로 ±1 갱신한다.
 * AFTER_COMMIT 으로 like 트랜잭션과 분리(@Async)하고, 유실 시에는 주기 스케줄러가 SSOT 기준으로 교정한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductLikeCountListener {

    private final ProductRepository productRepository;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(ProductLikeChangedEvent event) {
        int updated = productRepository.incrementLikeCount(event.productId(), event.delta());
        if (updated == 0) {
            log.warn("like_count 갱신 대상 없음 productId={} delta={}", event.productId(), event.delta());
        }
    }
}
