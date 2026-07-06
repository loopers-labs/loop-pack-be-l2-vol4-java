package com.loopers.application.like;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 좋아요 집계(like_count) 리스너.
 *
 * <p>{@link Async} + {@link TransactionalEventListener}(AFTER_COMMIT) — 좋아요 커밋 이후
 * <strong>별도 스레드</strong>에서 집계를 반영한다.
 *
 * <p><strong>왜 @Async 인가 (커넥션 데드락 회피)</strong>: 동기 AFTER_COMMIT 은 원 트랜잭션의 커넥션이
 * 반납되기 <em>전</em>(cleanup 이전)에 실행된다. 여기서 새 트랜잭션(REQUIRES_NEW)을 열면 한 스레드가
 * 커넥션 2개를 동시에 점유하고, 동시 요청이 풀 크기의 절반을 넘으면 서로 두 번째 커넥션을 기다리다
 * 풀이 고갈되어 데드락한다(hold-and-wait). {@code @Async} 는 원 트랜잭션 커넥션이 반납된 뒤
 * 다른 스레드에서 커넥션을 1개만 사용하므로 이 문제가 없다.
 *
 * <p>실제 집계 UPDATE 는 {@link LikeCountSynchronizer}가 자체 트랜잭션에서 수행한다.
 * 집계 실패는 이미 커밋된 좋아요에 영향을 주지 않으며(최종 일관성), 예외를 삼켜 로그로만 남긴다
 * (운영 보정 수단: {@code ProductRepository#resyncAllLikeCounts}).
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class LikeCountEventListener {

    private final LikeCountSynchronizer likeCountSynchronizer;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLikeChanged(LikeChangedEvent event) {
        try {
            likeCountSynchronizer.apply(event);
        } catch (Exception e) {
            log.warn("[LikeCount] 좋아요 집계 실패 — drift 발생(resync 로 보정). productId={}, type={}",
                event.productId(), event.type(), e);
        }
    }
}
