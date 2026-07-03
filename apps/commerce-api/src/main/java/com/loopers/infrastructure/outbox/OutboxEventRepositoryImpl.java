package com.loopers.infrastructure.outbox;

import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

@RequiredArgsConstructor
@Component
public class OutboxEventRepositoryImpl implements OutboxEventRepository {

    private final OutboxEventJpaRepository outboxEventJpaRepository;

    @Override
    public OutboxEvent save(OutboxEvent event) {
        return outboxEventJpaRepository.save(event);
    }

    @Override
    public List<OutboxEvent> findPending(int limit) {
        return outboxEventJpaRepository.findByStatusOrderByIdAsc(OutboxEvent.Status.PENDING, PageRequest.of(0, limit));
    }

    @Override
    @Transactional
    public void markPublished(List<Long> ids) {
        if (ids.isEmpty()) {
            return;
        }
        outboxEventJpaRepository.updateStatusByIds(ids, OutboxEvent.Status.PUBLISHED, ZonedDateTime.now());
    }
}
