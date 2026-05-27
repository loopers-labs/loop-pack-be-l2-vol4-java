package com.loopers.domain.event.outbox;

import java.util.List;

public interface OrderEventOutboxRepository {
    OrderEventOutbox save(OrderEventOutbox outbox);

    List<OrderEventOutbox> findPendingEvents();

    default List<OrderEventOutbox> findPendingEventsForUpdate(int limit) {
        return findPendingEvents();
    }
}
