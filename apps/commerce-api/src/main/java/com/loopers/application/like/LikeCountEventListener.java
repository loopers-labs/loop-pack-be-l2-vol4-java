package com.loopers.application.like;

import com.loopers.domain.like.event.ProductLikedEvent;
import com.loopers.domain.like.event.ProductUnlikedEvent;
import com.loopers.domain.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 좋아요 비정규화 카운터({@code Product.likeCount}) 집계를 좋아요 트랜잭션과 분리해 반영한다.
 *
 * <ul>
 *   <li><b>AFTER_COMMIT</b>: 커밋된 좋아요만 집계한다. 집계 실패가 이미 성공한 좋아요를 되돌리지 못한다
 *       ("집계 실패 ≠ 좋아요 성공").</li>
 *   <li><b>@Async</b>: afterCommit 에서 작업을 풀에 넘기고 원 커넥션을 즉시 반납한다 → 요청 스레드가 커넥션을
 *       두 개 쥐지 않는다(동기 AFTER_COMMIT + REQUIRES_NEW 의 커넥션 2배 압박 회피). 별도 스레드에 트랜잭션
 *       컨텍스트가 전파되지 않으므로 {@code @Transactional} 이 새 트랜잭션을 연다.</li>
 * </ul>
 *
 * <p>대가: 카운터는 eventual(lag) 이며 best-effort 다 — 큐 적재 중 크래시하면 그 증분은 유실되고 재시도가 없다
 * (수렴 보장 아님). 보장된 수렴(내구성·재시도)은 Step 2 의 Kafka 소비자(product_metrics)가 담당한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LikeCountEventListener {

    private final ProductRepository productRepository;

    @Async("likeCountExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLiked(ProductLikedEvent event) {
        if (productRepository.increaseLikeCount(event.productId()) == 0) {
            log.warn("좋아요 집계 반영 실패(대상 상품 없음/삭제) — likeCount 드리프트 가능. productId={}", event.productId());
        }
    }

    @Async("likeCountExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUnliked(ProductUnlikedEvent event) {
        if (productRepository.decreaseLikeCount(event.productId()) == 0) {
            log.warn("좋아요 취소 집계 반영 실패(대상 상품 없음/삭제) — likeCount 드리프트 가능. productId={}", event.productId());
        }
    }
}
