package com.loopers.domain.metrics;

public interface EventHandledRepository {

    boolean exists(String eventId);

    EventHandledModel save(EventHandledModel eventHandled);
}