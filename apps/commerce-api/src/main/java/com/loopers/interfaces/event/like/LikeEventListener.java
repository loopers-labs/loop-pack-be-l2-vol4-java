package com.loopers.interfaces.event.like;

import com.loopers.application.like.LikeCountReflector;
import com.loopers.domain.like.LikeEventType;
import com.loopers.domain.like.LikedEvent;
import com.loopers.domain.like.UnlikedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@RequiredArgsConstructor
@Component
public class LikeEventListener {

    private final LikeCountReflector likeCountReflector;

    // fast-path 반영: 커밋 확정 후 비동기로 자기 이벤트만 직접 반영한다(best-effort, 릴레이 깨우기 아님).
    // 실패하거나 아직 안 끝나도 문제 없다 — OutboxRelay가 markDoneIfPending으로 멱등하게 다시 처리한다.
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void send(LikedEvent event) {
        reflectQuietly(event.eventId(), event.productId(), LikeEventType.LIKED_EVENT);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void send(UnlikedEvent event) {
        reflectQuietly(event.eventId(), event.productId(), LikeEventType.UNLIKED_EVENT);
    }

    // best-effort: 예외를 삼켜 릴레이에 재처리를 넘긴다. 커밋 확정 후 비동기 경로라 여기서 던져봐야
    // 되돌릴 트랜잭션도 없고 AsyncUncaughtException 로그 소음만 남는다.
    private void reflectQuietly(String eventId, Long productId, LikeEventType eventType) {
        try {
            likeCountReflector.reflect(eventId, productId, eventType);
        } catch (Exception e) {
            log.warn("fast-path 반영 실패, 릴레이가 재처리한다. eventId={}, eventType={}", eventId, eventType, e);
        }
    }
}
