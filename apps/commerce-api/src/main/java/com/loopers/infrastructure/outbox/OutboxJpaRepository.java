package com.loopers.infrastructure.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxJpaRepository extends JpaRepository<OutboxEventEntity, Long> {
}
