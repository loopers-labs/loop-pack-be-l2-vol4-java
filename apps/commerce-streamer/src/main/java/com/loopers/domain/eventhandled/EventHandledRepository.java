package com.loopers.domain.eventhandled;

public interface EventHandledRepository {
    boolean existsById(String eventId);
    EventHandledModel save(EventHandledModel model);
}
