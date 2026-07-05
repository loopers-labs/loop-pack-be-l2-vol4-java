package com.loopers.domain.event.outbox;

import java.util.List;

public interface EventOutboxRepository {
    EventOutbox save(EventOutbox outbox);

    List<EventOutbox> findPendingEvents(int limit);

    default List<EventOutbox> findPendingEvents() {
        return findPendingEvents(100);
    }
}
