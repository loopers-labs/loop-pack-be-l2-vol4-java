package com.loopers.domain.outbox;

import java.util.List;

public interface OutboxEventRepository {

    /**
     * 발행할 이벤트를 outbox 에 적재한다.
     * 호출 측(비즈니스 변경)의 트랜잭션에 참여하므로, 그 트랜잭션이 롤백되면 이 적재도 함께 사라진다.
     */
    OutboxEvent append(OutboxEvent event);

    /**
     * 아직 발행되지 않은(published=false) 이벤트를 오래된 순서로 최대 limit 건 조회한다. (Relay 폴링용)
     */
    List<OutboxEvent> findUnpublished(int limit);
}
