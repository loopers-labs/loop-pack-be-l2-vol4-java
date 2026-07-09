package com.loopers.infrastructure.eventlog;

import com.loopers.domain.eventlog.EventHandled;
import com.loopers.domain.eventlog.EventHandledId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventHandledJpaRepository extends JpaRepository<EventHandled, EventHandledId> {
}
