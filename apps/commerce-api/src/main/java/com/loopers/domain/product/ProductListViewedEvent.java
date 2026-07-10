package com.loopers.domain.product;

import com.loopers.domain.UserActivityEvent;

import java.util.UUID;

// 상품 목록 조회(GET /products) — 상세 진입 전 탐색/클릭에 해당하는 행동으로 간주한다.
// 비로그인으로도 호출 가능해 userId를 알 수 없고, brandId 미지정 시 전체 목록 조회다.
public record ProductListViewedEvent(String eventId, Long brandId) implements UserActivityEvent {

    public static ProductListViewedEvent of(Long brandId) {
        return new ProductListViewedEvent(UUID.randomUUID().toString(), brandId);
    }

    @Override
    public Long userId() {
        return null;
    }

    @Override
    public String aggregateType() {
        return "Product";
    }

    @Override
    public String aggregateId() {
        return brandId != null ? String.valueOf(brandId) : "ALL";
    }

    @Override
    public String eventType() {
        return "PRODUCT_LIST_VIEWED";
    }
}
