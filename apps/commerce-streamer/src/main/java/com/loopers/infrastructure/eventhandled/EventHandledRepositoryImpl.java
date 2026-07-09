package com.loopers.infrastructure.eventhandled;

import com.loopers.domain.eventhandled.EventHandledModel;
import com.loopers.domain.eventhandled.EventHandledRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class EventHandledRepositoryImpl implements EventHandledRepository {

    private final EventHandledJpaRepository eventHandledJpaRepository;

    @Override
    public boolean existsById(String eventId) {
        return eventHandledJpaRepository.existsById(eventId);
    }

    @Override
    public EventHandledModel save(EventHandledModel model) {
        return eventHandledJpaRepository.save(model);
    }
}
