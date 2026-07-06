package com.loopers.outbox.infrastructure;

import com.loopers.outbox.domain.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * PENDING 행을 id 순으로 잠그며 가져온다. 이미 다른 인스턴스가 잠근 행은 SKIP LOCKED 로 건너뛴다.
     * (MySQL 8.0+ / 트랜잭션 안에서 호출되어야 잠금이 유효)
     */
    @Query(value = """
            SELECT * FROM outbox_event
            WHERE status = 'PENDING'
            ORDER BY id
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> findPendingForUpdate(@Param("limit") int limit);
}
