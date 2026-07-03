package com.loopers.application.productlike;

import com.loopers.domain.product.ProductService;
import com.loopers.domain.productlike.ProductLikedEvent;
import com.loopers.domain.productlike.ProductUnlikedEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 좋아요 도메인 이벤트를 듣고 비정규화 like_count 집계를 갱신하는 리스너.
 * <p>
 * productlike 이벤트를 받아 product 도메인 서비스를 호출하는 도메인 간 조율이므로 application 레이어에 둔다.
 * <p>
 * 설계 결정:
 * <ul>
 *   <li>{@code AFTER_COMMIT}: 좋아요 트랜잭션이 커밋된 뒤 집계 → 집계 실패가 좋아요를 롤백시키지 않는다.</li>
 *   <li>{@code @Async}: 집계를 요청 스레드가 아닌 전용 executor(단일 스레드)에서 실행한다. 요청 스레드는
 *       좋아요 커밋 후 커넥션을 반환하고 곧바로 응답하므로, 동기 AFTER_COMMIT에서 발생하던 커넥션 2배 점유
 *       (요청 커넥션 + REQUIRES_NEW 커넥션)로 인한 풀 self-deadlock이 사라진다. 집계는 eventual consistency로 다룬다.</li>
 *   <li>예외 삼킴: 집계 실패가 이미 커밋된 좋아요에 영향을 주지 않도록 여기서 잡아 로깅만 한다.</li>
 * </ul>
 * 별도 스레드에서 실행되므로 이 시점엔 활성 트랜잭션이 없어, {@code increaseLikeCount}의 기본 전파(REQUIRED)가
 * 자연히 새 트랜잭션을 열고 커밋한다. (동기 AFTER_COMMIT 때 필요했던 REQUIRES_NEW가 더 이상 필요 없다.)
 */
@RequiredArgsConstructor
@Component
public class ProductLikeEventHandler {

    private static final Logger log = LoggerFactory.getLogger(ProductLikeEventHandler.class);

    private final ProductService productService;

    @Async("likeEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProductLiked(ProductLikedEvent event) {
        try {
            productService.increaseLikeCount(event.productId());
        } catch (Exception e) {
            log.error("좋아요 집계(증가) 실패 — 좋아요는 커밋됨. productId={}, userId={}",
                event.productId(), event.userId(), e);
        }
    }

    @Async("likeEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProductUnliked(ProductUnlikedEvent event) {
        try {
            productService.decreaseLikeCount(event.productId());
        } catch (Exception e) {
            log.error("좋아요 집계(감소) 실패 — 좋아요 취소는 커밋됨. productId={}, userId={}",
                event.productId(), event.userId(), e);
        }
    }
}
