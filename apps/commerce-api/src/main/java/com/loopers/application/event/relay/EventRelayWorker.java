package com.loopers.application.event.relay;

import com.loopers.domain.event.outbox.EventOutbox;
import com.loopers.domain.event.outbox.EventOutboxRepository;
import com.loopers.support.monitoring.EventMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@RequiredArgsConstructor
@Component
public class EventRelayWorker {

    private static final int BATCH_SIZE = 100;

    private final EventOutboxRepository eventOutboxRepository;
    private final EventMessagePublisher eventMessagePublisher;
    private final EventRelayResultService eventRelayResultService;
    private final EventMetrics eventMetrics;

    public List<RelayResult> relayPendingEvents() {
        return eventOutboxRepository.findPendingEvents(BATCH_SIZE)
            .stream()
            .map(this::relay)
            .toList();
    }

    private RelayResult relay(EventOutbox outbox) {
        if (!outbox.isPending()) {
            return RelayResult.noop(outbox.getAggregateId());
        }

        long startedAt = System.nanoTime();
        EventPublishResult result = eventMessagePublisher.publish(outbox);
        Duration duration = Duration.ofNanos(System.nanoTime() - startedAt);
        if (result.succeeded()) {
            eventMetrics.recordOutboxRelaySuccess(outbox.getTopic(), outbox.getEventType(), duration);
            return eventRelayResultService.markSent(outbox);
        }

        eventMetrics.recordOutboxRelayFailure(outbox.getTopic(), outbox.getEventType(), duration);
        return eventRelayResultService.recordFailure(outbox);
    }

    public record RelayResult(Status status, String aggregateId) {
        public enum Status {
            SENT,
            RETRY,
            FAILED,
            NOOP
        }

        public static RelayResult sent(String aggregateId) {
            return new RelayResult(Status.SENT, aggregateId);
        }

        public static RelayResult retry(String aggregateId) {
            return new RelayResult(Status.RETRY, aggregateId);
        }

        public static RelayResult failed(String aggregateId) {
            return new RelayResult(Status.FAILED, aggregateId);
        }

        private static RelayResult noop(String aggregateId) {
            return new RelayResult(Status.NOOP, aggregateId);
        }
    }
}
