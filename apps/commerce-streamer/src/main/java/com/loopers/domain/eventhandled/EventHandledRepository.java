package com.loopers.domain.eventhandled;

public interface EventHandledRepository {
    boolean existsByEventId(String eventId);
    EventHandledModel save(EventHandledModel eventHandled);
}
