package com.loopers.domain.outbox;

import java.util.List;

public interface OutboxEventRepository {

    OutboxEvent save(OutboxEvent event);

    /**
     * 미발행(PENDING) 이벤트를 오래된 순으로 조회한다. 발행기(relay) 폴링에 사용한다.
     * 여러 인스턴스가 동시에 폴링해도 같은 행을 잡지 않도록 행 잠금 + SKIP LOCKED로 조회한다.
     */
    List<OutboxEvent> findPendingForDispatch(int limit);
}
