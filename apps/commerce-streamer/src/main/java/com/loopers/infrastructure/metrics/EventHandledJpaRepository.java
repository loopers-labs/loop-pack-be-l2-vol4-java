package com.loopers.infrastructure.metrics;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EventHandledJpaRepository extends JpaRepository<EventHandledJpaEntity, String> {
}
