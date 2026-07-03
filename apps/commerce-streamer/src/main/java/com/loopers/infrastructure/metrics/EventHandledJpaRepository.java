package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.EventHandledModel;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventHandledJpaRepository extends JpaRepository<EventHandledModel, String> {
}
