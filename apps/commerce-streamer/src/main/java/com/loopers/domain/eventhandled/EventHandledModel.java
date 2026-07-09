package com.loopers.domain.eventhandled;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * 처리 완료된 이벤트의 ID만 기록하는 멱등 처리 테이블.
 * 이벤트 처리 이력(로그)과 분리하는 이유: event_handled는 "처리 여부"만 빠르게 조회하면 되는 좁고 항상-작은 테이블이고,
 * 로그 테이블은 감사/디버깅 목적의 append-only 이력으로 보존 기간·조회 패턴이 다르기 때문이다.
 */
@Getter
@Entity
@Table(name = "event_handled")
public class EventHandledModel extends BaseEntity {

    @Column(name = "event_id", nullable = false, updatable = false, unique = true, length = 36)
    private String eventId;

    protected EventHandledModel() {}

    public EventHandledModel(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("이벤트 ID는 필수입니다.");
        }
        this.eventId = eventId;
    }
}
