package com.loopers.infrastructure.event.outbox;

import com.loopers.domain.event.outbox.OutboxStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrderEventOutboxJpaRepository extends JpaRepository<OrderEventOutboxJpaEntity, Long> {
    List<OrderEventOutboxJpaEntity> findByStatusOrderByCreatedAtAsc(OutboxStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select o
        from OrderEventOutboxJpaEntity o
        where o.status = :status
        order by o.createdAt asc
        """)
    List<OrderEventOutboxJpaEntity> findByStatusForUpdate(@Param("status") OutboxStatus status, Pageable pageable);
}
