package com.loopers.infrastructure.metrics;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 컨슈머 멱등 저장소(event_handled) 접근기. {@code (consumer_group, event_id)} 복합 PK 로 그룹별 1회 처리를 보장한다.
 *
 * <p>처리 흐름(같은 트랜잭션): (1) 배치의 eventId 중 이미 처리된 것을 {@link #findHandled}로 걸러내고,
 * (2) 남은 fresh 이벤트만 집계에 반영한 뒤, (3) {@link #markHandled}로 마킹한다. 집계 반영과 마킹이 한
 * 트랜잭션이라, 커밋 실패로 재전달되면 아무것도 반영·마킹되지 않아 재처리가 안전하고(at-least-once),
 * 커밋된 뒤 재전달되면 findHandled 가 걸러 이중 반영을 막는다. 예외 기반 제어(중복키 캐치) 대신
 * 조회-후-필터 방식이라 트랜잭션이 오염되지 않는다.
 */
@Component
@RequiredArgsConstructor
public class EventHandledRepository {

    private final JdbcTemplate jdbcTemplate;

    /** 주어진 eventId 중 이 그룹이 이미 처리한 것들을 반환한다. */
    public Set<Long> findHandled(String consumerGroup, Collection<Long> eventIds) {
        if (eventIds.isEmpty()) {
            return Set.of();
        }
        String placeholders = String.join(",", Collections.nCopies(eventIds.size(), "?"));
        List<Object> params = new ArrayList<>(eventIds.size() + 1);
        params.add(consumerGroup);
        params.addAll(eventIds);
        List<Long> handled = jdbcTemplate.queryForList(
                "SELECT event_id FROM event_handled WHERE consumer_group = ? AND event_id IN (" + placeholders + ")",
                Long.class, params.toArray());
        return new HashSet<>(handled);
    }

    /** fresh eventId 들을 처리 완료로 마킹한다(같은 트랜잭션에서 집계 반영 직후 호출). */
    public void markHandled(String consumerGroup, Collection<Long> eventIds) {
        if (eventIds.isEmpty()) {
            return;
        }
        List<Object[]> batchArgs = eventIds.stream()
                .map(id -> new Object[]{consumerGroup, id})
                .toList();
        jdbcTemplate.batchUpdate(
                "INSERT INTO event_handled (consumer_group, event_id, handled_at) VALUES (?, ?, now())", batchArgs);
    }
}
