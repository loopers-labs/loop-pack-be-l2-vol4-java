package com.loopers.infrastructure.event.handled;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EventHandledJpaRepository extends JpaRepository<EventHandledJpaEntity, String> {
}
