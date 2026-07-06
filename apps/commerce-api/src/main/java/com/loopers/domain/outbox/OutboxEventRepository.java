package com.loopers.domain.outbox;

import java.util.List;

public interface OutboxEventRepository {
    OutboxEvent save(OutboxEvent outboxEvent);

    /** 미발행(PENDING) 이벤트를 오래된 순으로 조회한다 — 발행 순서가 파티션 내 순서가 된다. */
    List<OutboxEvent> findPending(int limit);
}
