package com.loopers.domain.idempotency;

public interface EventHandledRepository {

    /**
     * event_id 가 처음 보는 것이면 처리 표시(INSERT)하고 true, 이미 처리된 것이면 false 를 반환한다.
     * 호출 측은 true 일 때만 본 처리를 수행해 멱등을 달성한다 — 단, 표시와 본 처리는 한 트랜잭션이어야 한다.
     */
    boolean markIfFirst(String eventId);
}
