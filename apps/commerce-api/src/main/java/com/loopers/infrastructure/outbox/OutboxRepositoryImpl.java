package com.loopers.infrastructure.outbox;

import com.loopers.domain.outbox.OutboxModel;
import com.loopers.domain.outbox.OutboxRepository;
import com.loopers.domain.outbox.OutboxStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class OutboxRepositoryImpl implements OutboxRepository {

    private final OutboxJpaRepository outboxJpaRepository;

    @Override
    public OutboxModel save(OutboxModel outbox) {
        return outboxJpaRepository.save(outbox);
    }

    @Override
    public List<OutboxModel> findAllByStatusOrderByIdAsc(OutboxStatus status) {
        return outboxJpaRepository.findAllByStatusOrderByIdAsc(status);
    }

    @Override
    public boolean markDoneIfPending(String eventId) {
        return outboxJpaRepository.markDoneIfPending(eventId, OutboxStatus.DONE, OutboxStatus.PENDING) > 0;
    }
}
