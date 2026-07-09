package com.loopers.application.like;

import com.loopers.application.outbox.OutboxAppender;
import com.loopers.domain.like.LikeChangedEvent;
import com.loopers.infrastructure.outbox.EventTopics;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * {@link LikeChangedEvent}를 좋아요 트랜잭션 <b>커밋 직전(BEFORE_COMMIT)</b>에 outbox로 적재한다.
 * 도메인(LikeService)이 Kafka/Outbox를 모르도록 시스템 간 전파 관심사를 분리한 어댑터.
 *
 * <p><b>왜 BEFORE_COMMIT인가</b>: 좋아요 행 변경과 outbox INSERT를 <b>같은 트랜잭션</b>으로 묶어 원자적으로
 * 커밋/롤백한다. 좋아요가 롤백되면 outbox 행도 사라지고, 커밋되면 outbox 행도 반드시 함께 남는다
 * → "메시지 없는 좋아요"도 "고아 이벤트"도 없다(At Least Once의 발행 보장). 실제 Kafka 전송은
 * {@code OutboxRelay}가 커밋 이후 비동기로 담당한다. (옛 AFTER_COMMIT 직접 send 방식은 "커밋됐는데 send
 * 실패" 시 유실되는 at-most-once 구간이 있어 폐기했다.)
 *
 * <p>{@code key=productId}: 같은 상품 이벤트가 같은 파티션으로 가서 소비자가 순서대로/상품별로 합산할 수 있다.
 * 좋아요 카운트는 가산(델타 합)이므로 version은 최신성 비교에 쓰이지 않는다 → 0.
 */
@Component
@RequiredArgsConstructor
public class LikeEventOutboxListener {

    private final OutboxAppender outboxAppender;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void on(LikeChangedEvent event) {
        outboxAppender.append(
                "product",
                event.productId(),
                "LIKE_CHANGED",
                EventTopics.CATALOG_EVENTS,
                String.valueOf(event.productId()),
                event,
                0L
        );
    }
}
