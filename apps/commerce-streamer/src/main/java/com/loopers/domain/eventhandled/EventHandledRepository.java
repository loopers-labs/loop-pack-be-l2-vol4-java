package com.loopers.domain.eventhandled;

public interface EventHandledRepository {

    boolean existsByEventIdAndConsumerGroup(String eventId, String consumerGroup);

    EventHandledRecord save(EventHandledRecord record);
}
