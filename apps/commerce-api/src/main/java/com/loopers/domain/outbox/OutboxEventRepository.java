package com.loopers.domain.outbox;

import java.util.List;
import java.util.Optional;

public interface OutboxEventRepository {
    OutboxEventModel save(OutboxEventModel outboxEvent);
    Optional<OutboxEventModel> findById(Long id);
    List<OutboxEventModel> findPending(int limit);
}
