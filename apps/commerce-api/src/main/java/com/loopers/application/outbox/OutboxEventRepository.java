package com.loopers.application.outbox;

import java.util.List;
import java.util.Optional;

public interface OutboxEventRepository {
    OutboxEvent save(OutboxEvent event);
    List<OutboxEvent> findRelayableEvents(int limit);
    Optional<OutboxEvent> findByEventId(String eventId);
}
