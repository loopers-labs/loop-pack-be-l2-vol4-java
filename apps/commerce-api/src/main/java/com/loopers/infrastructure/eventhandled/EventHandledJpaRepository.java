package com.loopers.infrastructure.eventhandled;

import com.loopers.domain.eventhandled.EventHandledModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventHandledJpaRepository extends JpaRepository<EventHandledModel, String> {

    // MySQL 벤더 특화 INSERT IGNORE - JPQL로 표현할 수 없다.
    // affected rows가 1이면 이 호출이 최초 처리 권한을 획득한 것이고 0이면 이미 처리된 이벤트다.
    @Modifying
    @Query(value = "INSERT IGNORE INTO event_handled (event_id, handled_at) VALUES (:eventId, NOW())", nativeQuery = true)
    int markHandled(@Param("eventId") String eventId);
}
