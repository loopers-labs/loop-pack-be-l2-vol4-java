package com.loopers.interfaces.consumer;

/**
 * commerce-api가 발행하는 좋아요 변경 메시지의 컨슈머측 표현.
 * JSON 필드명(productId, delta)이 발행측 {@code LikeChangedEvent}와 일치해야 역직렬화된다.
 * (kafka.yml의 spring.json.add.type.headers=false → 타입 헤더 없이 컨슈머 메서드 시그니처 타입으로 매핑)
 */
public record LikeChangedMessage(Long productId, long delta) {
}
