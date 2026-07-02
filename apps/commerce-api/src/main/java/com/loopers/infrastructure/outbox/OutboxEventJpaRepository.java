package com.loopers.infrastructure.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEvent, Long> {

    /** relay 가 오래된 것부터(=삽입 순서) 배치로 집는다. id 오름차순이 곧 삽입 순서. */
    List<OutboxEvent> findTop100ByStatusOrderByIdAsc(OutboxStatus status);
}
