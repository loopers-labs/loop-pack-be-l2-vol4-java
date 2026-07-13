package com.loopers.queue.application.event;

import com.loopers.order.application.event.OrderCreatedEvent;
import com.loopers.queue.domain.EntryTokenStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 주문이 커밋되면 그 유저의 입장 토큰을 삭제한다(단발성). 주문 흐름은 대기열을 알지 않고,
 * 토큰 소비를 이벤트로 분리한다. 삭제 실패는 TTL 만료가 backstop 이므로 best-effort 다.
 */
@Component
@RequiredArgsConstructor
public class QueueTokenCleanupListener {

    private final EntryTokenStore entryTokenStore;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCreated(OrderCreatedEvent event) {
        entryTokenStore.remove(String.valueOf(event.userId()));
    }
}
