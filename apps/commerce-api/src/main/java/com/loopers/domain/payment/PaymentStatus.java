package com.loopers.domain.payment;

public enum PaymentStatus {
    PENDING,   // 결과 미확정 — 폴백 포함, 이후 콜백/폴링으로 정합성 보정
    SUCCESS,
    FAILED,
}
