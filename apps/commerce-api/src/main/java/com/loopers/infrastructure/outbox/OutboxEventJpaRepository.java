package com.loopers.infrastructure.outbox;

import com.loopers.domain.outbox.OutboxEventModel;
import com.loopers.domain.outbox.OutboxEventStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventModel, Long> {
    List<OutboxEventModel> findByStatusOrderByIdAsc(OutboxEventStatus status, Pageable pageable);
}
