package com.loopers.domain.outbox;

// outbox 릴레이가 eventType으로 디스패치하는 처리기.
// 새 이벤트 종류를 추가하려면 이 인터페이스를 구현한 빈을 등록하면 된다.
public interface OutboxEventHandler {

    boolean supports(String eventType);

    void handle(OutboxModel outbox);
}
