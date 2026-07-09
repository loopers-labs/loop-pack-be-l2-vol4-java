package com.loopers.domain.outbox;

import java.util.List;

public interface OutboxRepository {

    OutboxModel save(OutboxModel outbox);

    /** 미발행(PENDING) 아웃박스를 id 오름차순(발행 순서 보장)으로 최대 limit 건 조회한다. */
    List<OutboxModel> findPending(int limit);
}