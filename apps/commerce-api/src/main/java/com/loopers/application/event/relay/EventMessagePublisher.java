package com.loopers.application.event.relay;

import com.loopers.domain.event.outbox.EventOutbox;

public interface EventMessagePublisher {
    EventPublishResult publish(EventOutbox outbox);
}
