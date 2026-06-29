package com.loopers.infrastructure.idempotency;

import com.loopers.domain.idempotency.EventHandled;
import com.loopers.domain.idempotency.EventHandledRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EventHandledRepositoryImpl implements EventHandledRepository {

    private final EventHandledJpaRepository eventHandledJpaRepository;

    @Override
    public boolean markIfFirst(String eventId) {
        if (eventHandledJpaRepository.existsById(eventId)) {
            return false;
        }
        eventHandledJpaRepository.save(new EventHandled(eventId));
        return true;
    }
}
