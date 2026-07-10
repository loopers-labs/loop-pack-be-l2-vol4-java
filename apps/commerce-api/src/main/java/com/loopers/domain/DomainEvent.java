package com.loopers.domain;

// 이미 일어난 사실(fact)임을 나타내는 공통 이벤트 엔벨로프.
// CloudEvents(id/type/source/subject), Debezium outbox(id/aggregatetype/aggregateid/type)와 동일한 축의
// 범용 식별·분류 정보만 담는다 — 특정 전파 방식(outbox 등)에 대한 계약은 이 인터페이스를 확장해 별도로 추가한다.
public interface DomainEvent {

    String eventId();

    String aggregateType();

    String aggregateId();

    String eventType();
}
