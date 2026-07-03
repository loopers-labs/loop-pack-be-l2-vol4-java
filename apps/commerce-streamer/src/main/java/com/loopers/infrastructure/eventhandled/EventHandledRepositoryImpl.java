package com.loopers.infrastructure.eventhandled;

import com.loopers.domain.eventhandled.EventHandledRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class EventHandledRepositoryImpl implements EventHandledRepository {

    private final EventHandledJpaRepository eventHandledJpaRepository;

    @Override
    public boolean markHandled(String eventId) {
        return eventHandledJpaRepository.markHandled(eventId) > 0;
    }
}
