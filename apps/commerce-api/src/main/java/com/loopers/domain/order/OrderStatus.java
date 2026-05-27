package com.loopers.domain.order;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OrderStatus {

    CREATED("주문 생성됨");

    private final String description;
}
