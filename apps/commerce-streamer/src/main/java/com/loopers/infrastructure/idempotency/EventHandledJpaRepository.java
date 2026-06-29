package com.loopers.infrastructure.idempotency;

import com.loopers.domain.idempotency.EventHandled;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventHandledJpaRepository extends JpaRepository<EventHandled, String> {
}
