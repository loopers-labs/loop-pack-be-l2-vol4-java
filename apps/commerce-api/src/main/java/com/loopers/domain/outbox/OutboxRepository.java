package com.loopers.domain.outbox;

import java.util.List;

public interface OutboxRepository {

    OutboxModel save(OutboxModel outbox);

    List<OutboxModel> findAllByStatusOrderByIdAsc(OutboxStatus status);

    // 멱등성 보장: eventId 기준 UPDATE WHERE status='PENDING' 의 원자적 실행으로
    // 동시에 같은 이벤트를 처리하려는 인스턴스 중 하나만 true를 반환받는다.
    boolean markDoneIfPending(String eventId);
}
