package com.loopers.application.order;

import java.util.List;

/**
 * 주문 생성 유스케이스 입력 (application 레이어). 도메인 타입(OrderLine)을 interfaces 로 노출하지 않기 위한 경계.
 * couponId 는 미적용 시 null.
 */
public record PlaceOrderCommand(List<Item> items, Long couponId) {

    public record Item(Long productId, int quantity) {}
}
