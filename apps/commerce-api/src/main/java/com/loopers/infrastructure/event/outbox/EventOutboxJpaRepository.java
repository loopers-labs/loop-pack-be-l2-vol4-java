package com.loopers.infrastructure.event.outbox;

import com.loopers.domain.event.outbox.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventOutboxJpaRepository extends JpaRepository<EventOutboxJpaEntity, Long> {
    List<EventOutboxJpaEntity> findByStatusOrderByCreatedAtAsc(OutboxStatus status, Pageable pageable);
}
