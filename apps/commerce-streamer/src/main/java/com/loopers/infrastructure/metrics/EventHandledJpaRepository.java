package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.EventHandled;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface EventHandledJpaRepository extends JpaRepository<EventHandled, String> {

    /** 배치 멱등 가드 — 배치 내 eventId 중 이미 처리된 것들만 한 번의 쿼리로 조회한다(건별 existsById 회피). */
    @Query("SELECT e.eventId FROM EventHandled e WHERE e.eventId IN :eventIds")
    List<String> findHandledIds(@Param("eventIds") Collection<String> eventIds);
}
