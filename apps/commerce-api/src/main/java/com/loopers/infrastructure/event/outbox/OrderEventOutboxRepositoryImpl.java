package com.loopers.infrastructure.event.outbox;

import com.loopers.domain.event.outbox.OrderEventOutbox;
import com.loopers.domain.event.outbox.OrderEventOutboxRepository;
import com.loopers.domain.event.outbox.OutboxStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class OrderEventOutboxRepositoryImpl implements OrderEventOutboxRepository {

    private final OrderEventOutboxJpaRepository orderEventOutboxJpaRepository;

    @Override
    public OrderEventOutbox save(OrderEventOutbox outbox) {
        OrderEventOutboxJpaEntity entity = outbox.isNew()
            ? OrderEventOutboxJpaEntity.from(outbox)
            : orderEventOutboxJpaRepository.findById(outbox.getId()).orElseGet(() -> OrderEventOutboxJpaEntity.from(outbox));
        entity.apply(outbox);
        return orderEventOutboxJpaRepository.save(entity).toDomain();
    }

    @Override
    public List<OrderEventOutbox> findPendingEvents() {
        return orderEventOutboxJpaRepository.findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING)
            .stream()
            .map(OrderEventOutboxJpaEntity::toDomain)
            .toList();
    }

    @Override
    public List<OrderEventOutbox> findPendingEventsForUpdate(int limit) {
        int size = limit <= 0 ? 100 : limit;
        return orderEventOutboxJpaRepository.findByStatusForUpdate(OutboxStatus.PENDING, PageRequest.of(0, size))
            .stream()
            .map(OrderEventOutboxJpaEntity::toDomain)
            .toList();
    }
}
