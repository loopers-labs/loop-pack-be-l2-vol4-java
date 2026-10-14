package com.loopers.domain.eventpublish;

import java.util.List;
import java.util.Optional;

public interface OutboxRepository {
    OutboxMessage save(OutboxMessage message);

    Optional<OutboxMessage> findById(Long id);

    /**
     * PENDING 상태 메시지를 오래된 순으로 batch 크기만큼 조회.
     * Relay 가 주기적으로 호출한다.
     */
    List<OutboxMessage> findPendingBatch(int limit);

    /**
     * eventId 기반 조회 — 중복 발행 여부 확인 시 사용.
     */
    Optional<OutboxMessage> findByEventId(String eventId);
}
