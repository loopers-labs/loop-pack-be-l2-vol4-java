package com.loopers.application.like;

import com.loopers.domain.like.event.ProductLikedEvent;
import com.loopers.domain.like.event.ProductUnlikedEvent;
import com.loopers.domain.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 좋아요 집계(products.like_count) 반영 — 좋아요 등록/취소 트랜잭션 커밋 후 별도 트랜잭션에서 처리한다.
 * 집계 반영이 실패해도 이미 커밋된 좋아요 등록/취소 자체는 되돌리지 않는다(eventual consistency).
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class LikeCountEventListener {

    private final ProductRepository productRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(ProductLikedEvent event) {
        try {
            productRepository.incrementLikeCount(event.productId());
        } catch (Exception e) {
            log.error("좋아요 집계 반영 실패 — productId={}, userId={}", event.productId(), event.userId(), e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(ProductUnlikedEvent event) {
        try {
            productRepository.decrementLikeCount(event.productId());
        } catch (Exception e) {
            log.error("좋아요 취소 집계 반영 실패 — productId={}, userId={}", event.productId(), event.userId(), e);
        }
    }
}
