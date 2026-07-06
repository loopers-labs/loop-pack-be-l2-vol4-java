package com.loopers.infrastructure.outbox;

import com.loopers.domain.outbox.OutboxEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.List;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEvent, Long> {

    List<OutboxEvent> findByStatusOrderByIdAsc(OutboxEvent.Status status, Pageable pageable);

    @Modifying
    @Query("UPDATE OutboxEvent o SET o.status = :status, o.publishedAt = :publishedAt WHERE o.id IN :ids")
    int updateStatusByIds(@Param("ids") List<Long> ids,
                          @Param("status") OutboxEvent.Status status,
                          @Param("publishedAt") ZonedDateTime publishedAt);
}
