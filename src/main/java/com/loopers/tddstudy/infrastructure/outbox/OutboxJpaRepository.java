package com.loopers.tddstudy.infrastructure.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OutboxJpaRepository extends JpaRepository<OutboxMessage, Long> {
    List<OutboxMessage> findTop100ByStatusOrderByIdAsc(String status);
}
