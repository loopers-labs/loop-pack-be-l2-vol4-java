package com.loopers.infrastructure.outbox;

import com.loopers.domain.outbox.OutboxModel;
import com.loopers.domain.outbox.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OutboxJpaRepository extends JpaRepository<OutboxModel, Long> {

    List<OutboxModel> findAllByStatusOrderByIdAsc(OutboxStatus status);

    // affected rows가 1이면 이 인스턴스가 처리 권한을 획득한 것이고 0이면 이미 다른 인스턴스가 처리 완료
    @Modifying
    @Query("UPDATE OutboxModel o SET o.status = :done WHERE o.eventId = :eventId AND o.status = :pending")
    int markDoneIfPending(@Param("eventId") String eventId, @Param("done") OutboxStatus done, @Param("pending") OutboxStatus pending);
}
