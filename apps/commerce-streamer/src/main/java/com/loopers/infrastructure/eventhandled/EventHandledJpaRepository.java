package com.loopers.infrastructure.eventhandled;

import com.loopers.domain.eventhandled.EventHandledRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventHandledJpaRepository extends JpaRepository<EventHandledRecord, Long> {
    boolean existsByEventIdAndConsumerGroup(String eventId, String consumerGroup);
}
