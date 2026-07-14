package com.loopers.application.outbox;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

@RequiredArgsConstructor
@Component
public class OutboxRelayService {

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxMessagePublisher outboxMessagePublisher;
    private final OutboxProperties outboxProperties;
    private final MeterRegistry meterRegistry;

    @Transactional
    public void relayReadyEvents() {
        for (OutboxEvent event : outboxEventRepository.findRelayableEvents(outboxProperties.batchSize())) {
            try {
                outboxMessagePublisher.publish(event);
                event.markPublished(ZonedDateTime.now());
                meterRegistry.counter("outbox_publish_total", "result", "success", "eventType", event.getEventType()).increment();
            } catch (RuntimeException exception) {
                event.markFailed(exception.getClass().getSimpleName());
                meterRegistry.counter("outbox_publish_total", "result", "failure", "eventType", event.getEventType()).increment();
            }
            outboxEventRepository.save(event);
        }
    }
}
