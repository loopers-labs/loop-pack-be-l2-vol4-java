package com.loopers.product.application.event;

/**
 * 상품 상세가 조회된 사실. 조회는 DB 상태를 바꾸지 않는 읽기라 트랜잭션이 없으므로
 * Outbox 가 아니라 best-effort 직접 발행으로 집계에 전달한다(조회 수는 근사치 허용).
 */
public record ProductViewedEvent(Long productId) {
}
