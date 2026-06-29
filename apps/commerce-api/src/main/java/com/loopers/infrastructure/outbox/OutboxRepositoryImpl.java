package com.loopers.infrastructure.outbox;

import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class OutboxRepositoryImpl implements OutboxEventRepository {

    private final OutboxJpaRepository outboxJpaRepository;

    @Override
    public OutboxEvent append(OutboxEvent event) {
        return outboxJpaRepository.save(event);
    }

    @Override
    public List<OutboxEvent> findUnpublished(int limit) {
        return outboxJpaRepository.findByPublishedIsFalseOrderByIdAsc(PageRequest.of(0, limit));
    }
}
