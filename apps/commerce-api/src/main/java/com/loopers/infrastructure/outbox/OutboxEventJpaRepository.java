package com.loopers.infrastructure.outbox;

import com.loopers.application.outbox.OutboxEventStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventJpaEntity, Long> {
    List<OutboxEventJpaEntity> findByStatusInOrderByCreatedAtAsc(List<OutboxEventStatus> statuses, Pageable pageable);
    Optional<OutboxEventJpaEntity> findByEventId(String eventId);
}
