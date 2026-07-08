package com.loopers.application.outbox;

public interface OutboxMessagePublisher {
    void publish(OutboxEvent event);
}
