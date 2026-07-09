package com.loopers.infrastructure.outbox;

import com.loopers.domain.outbox.OutboxModel;
import com.loopers.domain.outbox.OutboxRepository;
import com.loopers.domain.outbox.OutboxStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class OutboxRepositoryImpl implements OutboxRepository {

    private final OutboxJpaRepository outboxJpaRepository;

    @Override
    public OutboxModel save(OutboxModel outbox) {
        return outboxJpaRepository.save(outbox);
    }

    @Override
    public List<OutboxModel> findPendingBatch(int limit) {
        return outboxJpaRepository.findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING, limit);
    }
}
