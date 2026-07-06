package com.loopers.application.activity;

/**
 * 상품 조회 이벤트 — 조회(클릭 포함) 행동 로깅용.
 *
 * <p>조회는 트랜잭션이 없거나 readOnly 인 읽기라, 커밋 개념이 없다.
 * 따라서 {@code AFTER_COMMIT}(활성 트랜잭션 필요) 대신 평범한 {@code @EventListener}로 즉시 로깅한다.
 *
 * <p>상품 조회 API 는 비로그인 접근을 허용하므로 {@code userId} 는 null 일 수 있다(익명 조회).
 */
public record ProductViewedEvent(Long userId, Long productId) {
}
