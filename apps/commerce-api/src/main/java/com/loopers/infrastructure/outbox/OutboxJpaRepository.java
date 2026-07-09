package com.loopers.infrastructure.outbox;

import com.loopers.domain.outbox.OutboxModel;
import com.loopers.domain.outbox.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface OutboxJpaRepository extends JpaRepository<OutboxModel, Long> {

    @Query("SELECT o FROM OutboxModel o WHERE o.status = :status ORDER BY o.createdAt ASC LIMIT :limit")
    List<OutboxModel> findByStatusOrderByCreatedAtAsc(OutboxStatus status, int limit);
}
