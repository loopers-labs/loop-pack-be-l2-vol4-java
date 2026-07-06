package com.loopers.domain.event;

public interface EventHandledRepository {
    boolean alreadyHandled(String eventId);

    void markHandled(String eventId);
}
