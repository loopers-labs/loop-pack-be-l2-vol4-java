package com.loopers.infrastructure.eventpublish;

import com.loopers.domain.eventpublish.OutboxMessage;
import com.loopers.domain.eventpublish.OutboxRepository;
import com.loopers.domain.eventpublish.OutboxStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class OutboxRepositoryImpl implements OutboxRepository {

    private final OutboxJpaRepository jpa;

    @Override
    public OutboxMessage save(OutboxMessage message) {
        return jpa.save(message);
    }

    @Override
    public Optional<OutboxMessage> findById(Long id) {
        return jpa.findById(id);
    }

    @Override
    public List<OutboxMessage> findPendingBatch(int limit) {
        return jpa.findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING, PageRequest.of(0, limit));
    }

    @Override
    public Optional<OutboxMessage> findByEventId(String eventId) {
        return jpa.findByEventId(eventId);
    }
}
