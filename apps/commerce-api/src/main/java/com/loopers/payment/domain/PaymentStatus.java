package com.loopers.payment.domain;

public enum PaymentStatus {
    PENDING,
    SUCCESS,
    FAILED,
    /** 정합성 보정으로도 회수 실패해 포기한 상태(진짜 PG 상태 미상) — 수동 확인 대상. terminal 이라 더는 재조회하지 않는다. */
    ABANDONED
}
