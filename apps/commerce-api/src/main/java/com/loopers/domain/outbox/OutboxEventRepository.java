package com.loopers.domain.outbox;

import java.util.List;

public interface OutboxEventRepository {

    OutboxEvent save(OutboxEvent event);

    /** 미발행(PENDING) 이벤트를 id 오름차순으로 조회 — 릴레이가 기록 순서대로 발행하기 위함. */
    List<OutboxEvent> findPending(int limit);

    /** 발행 확정 일괄 마킹 — 건별 UPDATE/커밋 대신 단일 벌크 UPDATE 로 릴레이 처리량을 확보한다. */
    void markPublished(List<Long> ids);
}
