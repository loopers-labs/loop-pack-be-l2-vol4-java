package com.loopers.application.like;

import com.loopers.domain.like.ProductLikedEvent;
import com.loopers.domain.like.ProductUnlikedEvent;
import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
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

    // AFTER_COMMIT 은 원본 트랜잭션 커밋 이후라, 여기서 DB 를 쓰려면 새 트랜잭션이 필요하다(REQUIRES_NEW).
    // 그렇지 않으면 이미 커밋된 트랜잭션에 합류해 UPDATE 가 커밋되지 않고 묻힌다.
    // @Async 가 없으면 요청 스레드가 원본 커넥션을 반납하기 전에 REQUIRES_NEW 용 커넥션을 추가로 빌려
    // (연산당 2개 점유) 동시 요청 시 풀이 고갈된다. 별도 스레드로 넘겨 원본 커넥션을 먼저 반납하게 한다.
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onProductLiked(ProductLikedEvent event) {
        productService.increaseLikeCount(event.productId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onProductUnliked(ProductUnlikedEvent event) {
        productService.decreaseLikeCount(event.productId());
    }
}
