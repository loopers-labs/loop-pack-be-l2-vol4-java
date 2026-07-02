package com.loopers.infrastructure.outbox;

import com.loopers.domain.outbox.OutboxModel;
import com.loopers.domain.outbox.OutboxRepository;
import com.loopers.domain.outbox.OutboxStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class OutboxRepositoryImpl implements OutboxRepository {

    private final OutboxJpaRepository outboxJpaRepository;

    @Override
    public OutboxModel save(OutboxModel outbox) {
        return outboxJpaRepository.save(outbox);
    }

    @Override
    public List<OutboxModel> findPending(int limit) {
        return outboxJpaRepository.findByStatusOrderByIdAsc(OutboxStatus.PENDING, PageRequest.of(0, limit));
    }
}