package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.EventHandledModel;
import com.loopers.domain.metrics.EventHandledRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EventHandledRepositoryImpl implements EventHandledRepository {

    private final EventHandledJpaRepository eventHandledJpaRepository;

    @Override
    public boolean exists(String eventId) {
        return eventHandledJpaRepository.existsById(eventId);
    }

    @Override
    public EventHandledModel save(EventHandledModel eventHandled) {
        return eventHandledJpaRepository.save(eventHandled);
    }
}