package com.loopers.infrastructure.event.outbox;

import com.loopers.domain.event.outbox.EventOutbox;
import com.loopers.domain.event.outbox.EventOutboxRepository;
import com.loopers.domain.event.outbox.OutboxStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class EventOutboxRepositoryImpl implements EventOutboxRepository {

    private final EventOutboxJpaRepository eventOutboxJpaRepository;

    @Override
    public EventOutbox save(EventOutbox outbox) {
        EventOutboxJpaEntity entity = outbox.isNew()
            ? EventOutboxJpaEntity.from(outbox)
            : eventOutboxJpaRepository.findById(outbox.getId()).orElseGet(() -> EventOutboxJpaEntity.from(outbox));
        entity.apply(outbox);
        return eventOutboxJpaRepository.save(entity).toDomain();
    }

    @Override
    public List<EventOutbox> findPendingEvents(int limit) {
        int size = limit <= 0 ? 100 : limit;
        return eventOutboxJpaRepository.findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING, PageRequest.of(0, size))
            .stream()
            .map(EventOutboxJpaEntity::toDomain)
            .toList();
    }
}
