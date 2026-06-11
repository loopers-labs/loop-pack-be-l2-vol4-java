package com.loopers.product.domain;

/**
 * 사용자에게 보여줄 표시 상태. 저장하지 않고 조회 시점에 status 와 재고로 계산한다.
 * (품절은 별도 상태로 저장하지 않고 재고로 판단 — 재고가 SSOT)
 */
public enum ProductDisplayStatus {
    ON_SALE,
    SOLD_OUT,
    SUSPENDED
}
