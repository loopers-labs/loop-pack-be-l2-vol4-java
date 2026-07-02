package com.loopers.domain.eventlog;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * event_handled 복합 PK = (event_id, handler). handler 를 키에 포함해 한 이벤트를 서로 다른 소비자가
 * 각자 한 번씩 처리하는 것을 허용한다(멱등 스코프 분리). {@code @EmbeddedId} 이므로 equals/hashCode 가 필수.
 */
@Getter
@Embeddable
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventHandledId implements Serializable {

    @Column(name = "event_id", nullable = false, updatable = false, length = 36)
    private String eventId;

    @Column(name = "handler", nullable = false, updatable = false, length = 100)
    private String handler;

    public EventHandledId(String eventId, String handler) {
        this.eventId = eventId;
        this.handler = handler;
    }
}
