package com.loopers.infrastructure.event;

import com.loopers.domain.event.EventHandled;
import com.loopers.domain.event.EventHandledRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class EventHandledRepositoryImpl implements EventHandledRepository {
    private final EventHandledJpaRepository eventHandledJpaRepository;

    @Override
    public boolean alreadyHandled(String eventId) {
        return eventHandledJpaRepository.existsById(eventId);
    }

    @Override
    public void markHandled(String eventId) {
        eventHandledJpaRepository.save(new EventHandled(eventId));
    }
}
