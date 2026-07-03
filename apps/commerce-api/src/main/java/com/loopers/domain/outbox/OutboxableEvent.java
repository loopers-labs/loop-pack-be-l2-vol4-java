package com.loopers.domain.outbox;

import com.loopers.domain.DomainEvent;

// outbox에 기록될 자격이 있는 이벤트임을 나타내는 마커. DomainEvent가 이미 기록에 필요한
// eventId/aggregateType/aggregateId/eventType을 제공하므로 별도 계약을 추가하지 않는다.
public interface OutboxableEvent extends DomainEvent {
}
