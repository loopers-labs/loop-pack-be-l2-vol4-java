package com.loopers.domain.eventhandled;

public interface EventHandledRepository {

    // 멱등성 보장: eventId 기준 INSERT IGNORE의 원자적 실행으로 동시에 같은 이벤트를 처리하려는
    // 인스턴스 중 하나만 true를 반환받는다. true면 이 호출이 최초 처리 권한을 획득한 것이다.
    boolean markHandled(String eventId);
}
