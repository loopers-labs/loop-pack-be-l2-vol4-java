package com.loopers.application.event.relay;

import com.loopers.domain.event.outbox.OrderEventOutbox;
import com.loopers.domain.event.outbox.OrderEventOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class EventRelayResultService {

    private static final int MAX_RETRY_COUNT = 3;

    private final OrderEventOutboxRepository orderEventOutboxRepository;

    @Transactional
    public EventRelayWorker.RelayResult markSent(OrderEventOutbox outbox) {
        outbox.markSent();
        orderEventOutboxRepository.save(outbox);
        return EventRelayWorker.RelayResult.sent(outbox.getOrderId());
    }

    @Transactional
    public EventRelayWorker.RelayResult recordFailure(OrderEventOutbox outbox) {
        outbox.recordFailure(MAX_RETRY_COUNT);
        orderEventOutboxRepository.save(outbox);
        if (outbox.isPending()) {
            return EventRelayWorker.RelayResult.retry(outbox.getOrderId());
        }

        return EventRelayWorker.RelayResult.failed(outbox.getOrderId());
    }
}
