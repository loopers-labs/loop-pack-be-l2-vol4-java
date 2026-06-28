package com.loopers.infrastructure.event.handled;

import com.loopers.domain.event.handled.EventHandled;
import com.loopers.domain.event.handled.EventHandledRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class EventHandledRepositoryImpl implements EventHandledRepository {

    private final EventHandledJpaRepository eventHandledJpaRepository;

    @Override
    public boolean exists(String eventId) {
        return eventHandledJpaRepository.existsById(eventId);
    }

    @Override
    public EventHandled save(EventHandled eventHandled) {
        return eventHandledJpaRepository.save(EventHandledJpaEntity.from(eventHandled)).toDomain();
    }
}
