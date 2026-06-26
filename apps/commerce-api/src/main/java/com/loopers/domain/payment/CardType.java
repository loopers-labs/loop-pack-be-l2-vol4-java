package com.loopers.domain.payment;

/**
 * 카드 종류. PG-Simulator 의 카드 종류와 1:1 매핑된다.
 * PG 가 새로 추가되어도 이 enum 은 카드사 단위라 그대로 유지된다.
 */
public enum CardType {
    SAMSUNG,
    KB,
    HYUNDAI,
    ;
}
