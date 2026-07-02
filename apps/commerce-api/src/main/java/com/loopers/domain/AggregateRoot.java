package com.loopers.domain;

import jakarta.persistence.Transient;

import java.util.ArrayList;
import java.util.List;

/**
 * 도메인 이벤트를 보관하는 애그리거트 루트 베이스.
 * 상태를 전이한 애그리거트가 그 사실(이벤트)을 스스로 등록하고(registerEvent),
 * 발행이라는 기술 행위는 응용 레이어가 수거(pullDomainEvents)해 수행한다.
 * 단, 생성 이벤트는 저장 전엔 ID 가 없으므로(IDENTITY) 여기 등록하지 않고 저장 직후 응용 레이어에서 발행한다.
 */
public abstract class AggregateRoot extends BaseEntity {

    @Transient
    private final transient List<Object> domainEvents = new ArrayList<>();

    protected void registerEvent(Object event) {
        domainEvents.add(event);
    }

    /** 쌓인 이벤트를 반환하고 비운다 — 같은 이벤트가 두 번 발행되지 않도록. */
    public List<Object> pullDomainEvents() {
        List<Object> events = List.copyOf(domainEvents);
        domainEvents.clear();
        return events;
    }
}
