package com.loopers.outbox.domain;

import java.util.List;

public interface OutboxEventRepository {

    OutboxEvent save(OutboxEvent event);

    /**
     * 아직 발행되지 않은(PENDING) 이벤트를 행 잠금(FOR UPDATE SKIP LOCKED)으로 가져온다.
     * 여러 릴레이 인스턴스가 동시에 호출해도 서로 다른 행을 집어 중복 발행을 막는다.
     */
    List<OutboxEvent> findPendingForUpdate(int limit);
}
