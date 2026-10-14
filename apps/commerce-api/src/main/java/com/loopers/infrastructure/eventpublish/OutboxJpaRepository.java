package com.loopers.infrastructure.eventpublish;

import com.loopers.domain.eventpublish.OutboxMessage;
import com.loopers.domain.eventpublish.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OutboxJpaRepository extends JpaRepository<OutboxMessage, Long> {

    List<OutboxMessage> findByStatusOrderByCreatedAtAsc(OutboxStatus status, Pageable pageable);

    Optional<OutboxMessage> findByEventId(String eventId);
}
