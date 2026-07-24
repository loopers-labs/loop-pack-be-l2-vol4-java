package com.loopers.infrastructure.outbox;

import com.loopers.domain.outbox.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * PENDING 이벤트를 id 오름차순(발행 순서)으로 잠금 조회한다.
     * {@code FOR UPDATE SKIP LOCKED}로 다른 인스턴스가 이미 잡은 행은 건너뛰어 중복 발행을 막는다.
     * (반드시 트랜잭션 안에서 호출해야 잠금이 유효하다.)
     */
    @Query(
        value = "SELECT * FROM outbox_event WHERE status = 'PENDING' ORDER BY id LIMIT :limit FOR UPDATE SKIP LOCKED",
        nativeQuery = true
    )
    List<OutboxEvent> findPendingForDispatch(int limit);
}
