package com.loopers.domain.payment;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CardType {

    SAMSUNG("삼성카드"),
    KB("KB국민카드"),
    HYUNDAI("현대카드");

    private final String description;
}
