package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.EventHandled;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventHandledJpaRepository extends JpaRepository<EventHandled, String> {
}
