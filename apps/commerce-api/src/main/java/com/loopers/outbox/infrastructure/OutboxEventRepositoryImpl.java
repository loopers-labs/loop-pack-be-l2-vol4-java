package com.loopers.outbox.infrastructure;

import com.loopers.outbox.domain.OutboxEvent;
import com.loopers.outbox.domain.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class OutboxEventRepositoryImpl implements OutboxEventRepository {

    private final OutboxEventJpaRepository jpaRepository;

    @Override
    public OutboxEvent save(OutboxEvent event) {
        return jpaRepository.save(event);
    }

    @Override
    public List<OutboxEvent> findPendingForUpdate(int limit) {
        return jpaRepository.findPendingForUpdate(limit);
    }
}
