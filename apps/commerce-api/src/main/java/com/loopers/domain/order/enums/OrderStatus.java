package com.loopers.domain.order.enums;

public enum OrderStatus {
    REQUESTED("주문 요청"),
    COMPLETED("주문 완료"),
    CANCELLED("주문 취소");

    private final String description;

    OrderStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
