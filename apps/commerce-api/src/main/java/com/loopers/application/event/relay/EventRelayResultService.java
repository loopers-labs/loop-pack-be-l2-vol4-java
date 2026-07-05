package com.loopers.application.event.relay;

import com.loopers.domain.event.outbox.EventOutbox;
import com.loopers.domain.event.outbox.EventOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class EventRelayResultService {

    private static final int MAX_RETRY_COUNT = 3;

    private final EventOutboxRepository eventOutboxRepository;

    @Transactional
    public EventRelayWorker.RelayResult markSent(EventOutbox outbox) {
        outbox.markSent();
        eventOutboxRepository.save(outbox);
        return EventRelayWorker.RelayResult.sent(outbox.getAggregateId());
    }

    @Transactional
    public EventRelayWorker.RelayResult recordFailure(EventOutbox outbox) {
        outbox.recordFailure(MAX_RETRY_COUNT);
        eventOutboxRepository.save(outbox);
        if (outbox.isPending()) {
            return EventRelayWorker.RelayResult.retry(outbox.getAggregateId());
        }

        return EventRelayWorker.RelayResult.failed(outbox.getAggregateId());
    }
}
