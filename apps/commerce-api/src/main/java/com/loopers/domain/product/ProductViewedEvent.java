package com.loopers.domain.product;

/**
 * "상품 상세가 조회되었다"는 사실을 통지하는 이벤트.
 * 조회 API 는 인증이 없어 userId 가 없다 — 상품 기준의 행동 로깅에 사용한다.
 * 도메인 상태 변화가 없는 요청 레벨 행동 이벤트라 애그리거트가 아닌 컨트롤러가 발행한다(도메인 이벤트 규칙의 예외).
 */
public record ProductViewedEvent(Long productId) {
}
