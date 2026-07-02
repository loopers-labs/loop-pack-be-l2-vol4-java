package com.loopers.domain.eventlog;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

/**
 * 소비자 멱등 원장. "이 handler 가 이 event_id 를 이미 처리했는가"를 기록한다.
 *
 * <p>broker→consumer 는 at-least-once 라 같은 메시지가 재전달될 수 있다. 처리 전 이 원장 존재 여부를 확인하고,
 * 없을 때만 집계 갱신 + 원장 INSERT 를 <b>같은 트랜잭션</b>으로 수행한다 → "집계만 되고 기록 누락" 혹은
 * "기록만 되고 집계 누락"이 생기지 않는다. 이미 있으면 skip → 중복 집계 차단(effectively-once).</p>
 */
@Getter
@Entity
@Table(name = "event_handled")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventHandled {

    @EmbeddedId
    private EventHandledId id;

    @Column(name = "handled_at", nullable = false, updatable = false)
    private ZonedDateTime handledAt;

    private EventHandled(EventHandledId id) {
        this.id = id;
    }

    public static EventHandled of(String eventId, String handler) {
        return new EventHandled(new EventHandledId(eventId, handler));
    }

    @PrePersist
    private void prePersist() {
        this.handledAt = ZonedDateTime.now();
    }
}
