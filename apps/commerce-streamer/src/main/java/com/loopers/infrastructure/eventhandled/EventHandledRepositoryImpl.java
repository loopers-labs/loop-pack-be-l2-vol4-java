package com.loopers.infrastructure.eventhandled;

import com.loopers.domain.eventhandled.EventHandledRecord;
import com.loopers.domain.eventhandled.EventHandledRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class EventHandledRepositoryImpl implements EventHandledRepository {

    private final EventHandledJpaRepository jpa;

    @Override
    public boolean existsByEventIdAndConsumerGroup(String eventId, String consumerGroup) {
        return jpa.existsByEventIdAndConsumerGroup(eventId, consumerGroup);
    }

    @Override
    public EventHandledRecord save(EventHandledRecord record) {
        return jpa.save(record);
    }
}
