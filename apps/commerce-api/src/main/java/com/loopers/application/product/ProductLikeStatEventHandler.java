package com.loopers.application.product;

import com.loopers.domain.like.event.ProductLikedEvent;
import com.loopers.domain.like.event.ProductUnlikedEvent;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductLikeStat;
import com.loopers.domain.product.ProductLikeStatRepository;
import com.loopers.domain.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 좋아요 등록/취소 이벤트를 받아 ProductLikeStat (read-model) 의 like_count 를 갱신한다.
 *
 *  - AFTER_COMMIT: 좋아요 트랜잭션이 정상 commit 된 뒤에만 실행 → like 와 stat 일관성 보장
 *  - REQUIRES_NEW: 핸들러는 별도 트랜잭션 → 사용자 응답 경로(like 트랜잭션)와 격리
 *
 * 동시성 한계 (개선 여지):
 *  dirty checking + @Version 으로 처리하므로 인기상품 동시 좋아요 시
 *  OptimisticLockingFailureException 이 빈번해질 수 있다. 원자적 UPDATE 쿼리로
 *  대체하면 락 경합을 최소화할 수 있다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class ProductLikeStatEventHandler {

    private final ProductLikeStatRepository productLikeStatRepository;
    private final ProductRepository productRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onLiked(ProductLikedEvent event) {
        try {
            getOrInit(event.productId()).increment();
        } catch (Exception e) {
            log.error("[ProductLikeStat] increment 실패 productId={}: {}", event.productId(), e.getMessage());
            // 운영 환경에선 retry / reconciliation batch 필요
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onUnliked(ProductUnlikedEvent event) {
        try {
            productLikeStatRepository.find(event.productId()).ifPresent(ProductLikeStat::decrement);
        } catch (Exception e) {
            log.error("[ProductLikeStat] decrement 실패 productId={}: {}", event.productId(), e.getMessage());
        }
    }

    /**
     * stat 이 없으면 product 의 brandId 를 가져와 0 으로 초기화한다.
     * 백필 단계(Stage 5)에서 모든 product 에 대해 미리 init 되므로 실제로는 보조 경로.
     */
    private ProductLikeStat getOrInit(Long productId) {
        return productLikeStatRepository.find(productId)
            .orElseGet(() -> {
                Product product = productRepository.find(productId)
                    .orElseThrow(() -> new IllegalStateException("Product not found: " + productId));
                return productLikeStatRepository.save(ProductLikeStat.init(product.getId(), product.getBrandId()));
            });
    }
}
