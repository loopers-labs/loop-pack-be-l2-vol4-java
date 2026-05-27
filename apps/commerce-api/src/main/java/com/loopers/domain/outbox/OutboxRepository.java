package com.loopers.domain.outbox;

public interface OutboxRepository {
    OutboxEvent save(OutboxEvent event);
}
