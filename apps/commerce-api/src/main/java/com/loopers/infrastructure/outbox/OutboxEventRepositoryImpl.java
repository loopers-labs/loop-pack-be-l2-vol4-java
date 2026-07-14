package com.loopers.infrastructure.outbox;

import com.loopers.application.outbox.OutboxEvent;
import com.loopers.application.outbox.OutboxEventRepository;
import com.loopers.application.outbox.OutboxEventStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class OutboxEventRepositoryImpl implements OutboxEventRepository {

    private final OutboxEventJpaRepository outboxEventJpaRepository;

    @Override
    public OutboxEvent save(OutboxEvent event) {
        OutboxEventJpaEntity entity = event.getId() == null
            ? OutboxEventJpaEntity.from(event)
            : outboxEventJpaRepository.findById(event.getId())
                .map(existing -> {
                    existing.update(event);
                    return existing;
                })
                .orElseGet(() -> OutboxEventJpaEntity.from(event));

        return outboxEventJpaRepository.save(entity).toDomain();
    }

    @Override
    public List<OutboxEvent> findRelayableEvents(int limit) {
        return outboxEventJpaRepository.findByStatusInOrderByCreatedAtAsc(
                List.of(OutboxEventStatus.READY, OutboxEventStatus.FAILED),
                PageRequest.of(0, limit)
            )
            .stream()
            .map(OutboxEventJpaEntity::toDomain)
            .toList();
    }

    @Override
    public Optional<OutboxEvent> findByEventId(String eventId) {
        return outboxEventJpaRepository.findByEventId(eventId)
            .map(OutboxEventJpaEntity::toDomain);
    }
}
