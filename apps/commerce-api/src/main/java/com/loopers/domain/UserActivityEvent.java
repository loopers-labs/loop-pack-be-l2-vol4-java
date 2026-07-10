package com.loopers.domain;

// 사용자 행동 로깅 대상 이벤트. userId()는 비로그인 접근(상품 조회 등)에서 null일 수 있다.
public interface UserActivityEvent extends DomainEvent {

    Long userId();
}
