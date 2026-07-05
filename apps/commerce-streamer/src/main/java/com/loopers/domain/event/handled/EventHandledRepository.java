package com.loopers.domain.event.handled;

public interface EventHandledRepository {
    boolean exists(String eventId);

    EventHandled save(EventHandled eventHandled);
}
