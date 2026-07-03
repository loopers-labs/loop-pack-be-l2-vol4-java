package com.loopers.interfaces.consumer;

/**
 * catalog-events 메시지(JSON). 프로듀서(commerce-api)의 CatalogEventPayload와 필드명이 일치해야 한다.
 */
public record CatalogEventMessage(String eventId, String type, Long productId, Long userId) {
}
